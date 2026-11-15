package com.tcem.workitem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.workitem.config.CouchDbProperties;
import com.tcem.workitem.config.WorkItemProperties;
import com.tcem.workitem.couchdb.InMemoryCouchDbOperations;
import com.tcem.workitem.producer.DeadLetterPublisher;
import com.tcem.workitem.producer.ReplayProducer;
import com.tcem.workitem.producer.WorkItemLinkedProducer;
import com.tcem.workitem.repository.AuditRepository;
import com.tcem.workitem.repository.DlqEntryRepository;
import com.tcem.workitem.repository.WorkItemRepository;
import com.tcem.workitem.service.AuditService;
import com.tcem.workitem.service.ConfirmationService;
import com.tcem.workitem.service.DlqService;
import com.tcem.workitem.service.ExceptionLinkingService;
import com.tcem.workitem.service.ManualReviewService;
import com.tcem.workitem.service.ReplayOutcomeService;
import com.tcem.workitem.service.ReplayService;
import com.tcem.workitem.service.TradeCancellationService;
import com.tcem.workitem.service.WorkItemService;
import org.springframework.kafka.core.KafkaOperations;

/**
 * Wires the real service graph on top of {@link InMemoryCouchDbOperations} and a mocked {@link KafkaOperations},
 * mirroring what Spring builds at runtime.
 */
public final class TestWiring {

    public final ObjectMapper objectMapper = DlqTestSupport.objectMapper();
    public final InMemoryCouchDbOperations couchDb = new InMemoryCouchDbOperations(objectMapper);
    public final CouchDbProperties couchProperties = DlqTestSupport.couchProperties();
    public final WorkItemProperties properties = new WorkItemProperties();

    public final DlqEntryRepository dlqRepository = new DlqEntryRepository(couchDb, couchProperties);
    public final WorkItemRepository workItemRepository = new WorkItemRepository(couchDb, couchProperties);
    public final AuditRepository auditRepository = new AuditRepository(couchDb, couchProperties);

    public final AuditService auditService = new AuditService(auditRepository);
    public final WorkItemService workItemService;
    public final ReplayOutcomeService replayOutcomeService;
    public final DlqService dlqService;
    public final ReplayService replayService;
    public final ExceptionLinkingService exceptionLinkingService;
    public final ManualReviewService manualReviewService;
    public final ConfirmationService confirmationService;
    public final TradeCancellationService tradeCancellationService;

    public TestWiring(KafkaOperations<String, String> kafka) {
        DlqTestSupport.stubSendsSucceed(kafka);
        workItemService = new WorkItemService(workItemRepository, new WorkItemLinkedProducer(kafka, objectMapper),
                auditService, properties);
        replayOutcomeService = new ReplayOutcomeService(objectMapper, dlqRepository, workItemService, auditService, properties);
        dlqService = new DlqService(objectMapper, dlqRepository, workItemService, auditService, replayOutcomeService, properties);
        replayService = new ReplayService(objectMapper, dlqRepository, new ReplayProducer(kafka), workItemService,
                auditService, properties);
        exceptionLinkingService = new ExceptionLinkingService(objectMapper, workItemService, auditService, properties);
        manualReviewService = new ManualReviewService(workItemService, new DeadLetterPublisher(kafka, objectMapper), objectMapper);
        tradeCancellationService = new TradeCancellationService(objectMapper, workItemService, auditService);
        confirmationService = new ConfirmationService(objectMapper, workItemService, auditService);
    }
}
