# Project Status & Team Responsibilities

This document tracks the progress of the Trade Confirmation Exception Management Platform based on the initial team roles and the work completed to date.

## 1. Mayank (Team Lead + `ingestion-service`)
**Role:** Coordination, event contracts, and seed/ingestion logic.
* **What is Done:** 
  * Successfully designed and implemented the shared `common-lib` schema (FpML-inspired JSON model).
  * Built the event envelopes (`TradeEvent`, `MessageHeader`) and canonical data models (`Trade`, `FxOptionEconomics`).
  * Implemented the `SchemaVersionRouter` to handle version evolution.
  * Created the `IdempotencyKey` pattern for exactly-once processing.
* **What is Remaining:** 
  * Implement the `ingestion-service` to accept synthetic events, run initial schema validations, and store raw payloads.
  * Lead coordination and code reviews for the integration of other services.

## 2. Gunjan (`enrichment-service` / Business Analyst)
**Role:** Reference data modelling and enrichment logic.
* **What is Done:** 
  * The schema is prepared for reference data integration, with `ReferenceDataEnrichment` and `EnrichmentSource` models created in `common-lib` to track data provenance.
* **What is Remaining:** 
  * Build the `enrichment-service`.
  * Model and implement the mock reference datasets (counterparty, instrument, template catalogue).
  * Integrate reference lookups to populate the `trade.enriched` topic.

## 3. Mahadev (`validation-service` / Backend Engineer)
**Role:** STP rules engine and confirmation APIs.
* **What is Done:** 
  * `common-lib` is equipped with `ConfirmableFieldRegistry` and `ConfirmationFingerprint` utilities. This solves the complex "not every attribute is confirmable" and duplicate suppression ("nothing changed") rules.
  * `ConfirmationRecord` and `ConfirmationEvent` models are ready.
* **What is Remaining:** 
  * Build the `validation-service`.
  * Implement the STP rules to evaluate enriched trades.
  * Connect the fingerprinting logic to suppress redundant confirmation generations.

## 4. Kapish (`exception-service` / Data Engineer)
**Role:** Exception capture, classification, and recommendation logic.
* **What is Done:** 
  * The exception taxonomy (`ExceptionCategory` and `ExceptionSubTheme`) is fully defined in `common-lib` matching the capstone spec.
  * `ExceptionRecord` and `ExceptionEvent` models are structured to support confidence scoring, SLA deadlines, and resolution metadata.
* **What is Remaining:** 
  * Implement the `exception-service`.
  * Build the deterministic rules engine (and optionally clustering/ML) to map failures to categories/themes.
  * Generate recommendation hints (e.g., owner queue, resolution difficulty).

## 5. Bhavik (`workitem-service` / DevOps Engineer)
**Role:** Work-item tracking, audit trail, DLQ/replay, and CI/CD.
* **What is Done:** 
  * Data structures for `WorkItem`, `AuditEvent`, and `DeadLetterEvent` are established.
  * The idempotency and replay protocol is fully documented and structured within the DLQ models (tracking `replayCount` and `replayable` flags).
* **What is Remaining:** 
  * Build the `workitem-service`.
  * Implement the storage layer for audit events, mock work items, and DLQ entries.
  * Implement the endpoint to clear idempotency keys and re-trigger events for the replay functionality.
  * Maintain Docker Compose and GitHub Actions pipelines.

## 6. Oren (`frontend` / Frontend & QA Engineer)
**Role:** UI dashboards and cross-service test coverage.
* **What is Done:** 
  * The backend JSON contracts are now stable, meaning the frontend has a clear data model to build against.
* **What is Remaining:** 
  * Build the React + TypeScript frontend application.
  * Develop dashboards for trade lifecycle, exception queues, aged items, and root-cause analysis.
  * Implement the manual review screen and connect to the DLQ replay controls.
  * Drive integration and E2E testing across the platform.

---
*Last updated after completion of the `common-lib` schema initialization.*
