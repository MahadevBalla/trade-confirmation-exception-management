package com.tcem.common.model.message;

/**
 * Canonical schema version string constants.
 * <p>
 * Rules:
 * <ul>
 *   <li>Never delete a version constant – older messages stored in CouchDB or
 *       the DLQ must always be deserializable.</li>
 *   <li>Adding a new optional field to an existing version is backward-compatible;
 *       increment {@code MINOR} only.</li>
 *   <li>Removing or renaming a field, or changing a type, requires a new
 *       {@code MAJOR} version string and a migration path in
 *       {@link com.tcem.common.routing.SchemaVersionRouter}.</li>
 * </ul>
 * </p>
 */
public final class SchemaVersions {

    private SchemaVersions() {}

    /**
     * Initial capstone version (spec §10 canonical example).
     * FX Option economics. All MVP services must produce and consume this version.
     */
    public static final String V1_0 = "capstone-fpml-json-1.0";

    /**
     * Placeholder for a future minor revision (e.g., additional optional fields).
     * Activate by registering a handler in {@link com.tcem.common.routing.SchemaVersionRouter}.
     */
    public static final String V1_1 = "capstone-fpml-json-1.1";

    /** The current default version used by producers. */
    public static final String CURRENT = V1_0;
}
