package jacross;

/**
 * Neutral semantic colour roles — deliberately named after neither
 * design language. ACCENT maps to Material's primary and Fluent's
 * colorBrandBackground; SURFACE_CONTAINER_HIGH to Material's role of
 * the same name and Fluent's colorNeutralBackground3.
 */
public enum ColorRole {
    SURFACE, SURFACE_CONTAINER_LOW, SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH,
    ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, OUTLINE_VARIANT,
    ACCENT, ON_ACCENT, ACCENT_CONTAINER, ON_ACCENT_CONTAINER,
    DANGER, FOCUS
}
