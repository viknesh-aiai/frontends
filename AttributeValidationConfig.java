package com.socgen.bigdata.catalog.services.attribute;

import com.socgen.bigdata.catalog.models.jpa.attribute.AttributeDataType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Predefined validation keys per dataType.
 *
 * Common to ALL types:
 *   required   — if true, a value must be provided when pushing values
 *
 * STRING:
 *   minLength  — minimum string length
 *   maxLength  — maximum string length
 *   pattern    — regex the value must match
 *
 * INTEGER / FLOAT:
 *   minimum    — minimum numeric value (inclusive)
 *   maximum    — maximum numeric value (inclusive)
 *
 * ARRAY:
 *   minItems   — minimum number of items in the array
 *   maxItems   — maximum number of items in the array
 *
 * BOOLEAN / OBJECT:
 *   no extra validations beyond required
 */
public class AttributeValidationConfig {

    private AttributeValidationConfig() {}

    private static final Set<String> STRING_VALIDATIONS  = Set.of("required", "minLength", "maxLength", "pattern");
    private static final Set<String> NUMERIC_VALIDATIONS = Set.of("required", "minimum", "maximum");
    private static final Set<String> ARRAY_VALIDATIONS   = Set.of("required", "minItems", "maxItems");
    private static final Set<String> BASIC_VALIDATIONS   = Set.of("required");

    private static final Map<AttributeDataType, Set<String>> ALLOWED_KEYS = Map.of(
        AttributeDataType.STRING,  STRING_VALIDATIONS,
        AttributeDataType.INTEGER, NUMERIC_VALIDATIONS,
        AttributeDataType.FLOAT,   NUMERIC_VALIDATIONS,
        AttributeDataType.BOOLEAN, BASIC_VALIDATIONS,
        AttributeDataType.ARRAY,   ARRAY_VALIDATIONS,
        AttributeDataType.OBJECT,  BASIC_VALIDATIONS
    );

    public static List<String> getInvalidKeys(AttributeDataType dataType, Set<String> providedKeys) {
        return providedKeys.stream()
            .filter(key -> !ALLOWED_KEYS.get(dataType).contains(key))
            .toList();
    }

    public static Set<String> getAllowedKeys(AttributeDataType dataType) {
        return ALLOWED_KEYS.get(dataType);
    }

    /**
     * Returns true if the attribute's properties mark it as required.
     * Used when validating pushed values — if required=true and no value provided, reject.
     */
    public static boolean isRequired(Map<String, Object> properties) {
        if (properties == null) return false;
        Object required = properties.get("required");
        if (required instanceof Boolean) return (Boolean) required;
        if (required instanceof String) return Boolean.parseBoolean((String) required);
        return false;
    }
}
