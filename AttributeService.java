package com.socgen.bigdata.catalog.services.attribute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeDefinitionRequestDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeDefinitionResponseDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueRequestDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueResponseDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeViewResponseDto;
import com.socgen.bigdata.catalog.models.jpa.attribute.Attribute;
import com.socgen.bigdata.catalog.models.jpa.attribute.AttributeDataType;
import com.socgen.bigdata.catalog.models.jpa.attribute.AttributeValue;
import com.socgen.bigdata.catalog.repositories.attribute.AttributeRepository;
import com.socgen.bigdata.catalog.repositories.attribute.AttributeValueRepository;
import com.socgen.bigdata.catalog.repositories.datasource.DataSourceRepository;
import com.socgen.bigdata.catalog.repositories.datasource.DataSourceTableRepository;
import com.socgen.bigdata.catalog.repositories.jpa.JpaEntityRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttributeService {

    private static final String OBJECT_TYPE_TABLES     = "Tables";
    private static final String OBJECT_TYPE_DATASOURCE = "DataSource";
    private static final String OBJECT_TYPE_ENTITIES   = "Entities";
    private static final List<String> ALLOWED_OBJECT_TYPES =
        List.of(OBJECT_TYPE_TABLES, OBJECT_TYPE_DATASOURCE, OBJECT_TYPE_ENTITIES);

    private final AttributeRepository        attributeRepository;
    private final AttributeValueRepository   attributeValueRepository;
    private final DataSourceTableRepository  dataSourceTableRepository;
    private final DataSourceRepository       dataSourceRepository;
    private final JpaEntityRepository        jpaEntityRepository;
    private final ObjectMapper               objectMapper;

    // -----------------------------------------------------------------------
    // POST #1 — Create or update attribute definitions
    // -----------------------------------------------------------------------

    @Transactional
    public AttributeDefinitionResponseDto createOrUpdateAttributeDefinitions(
        String objectType,
        AttributeDefinitionRequestDto requestDto
    ) {
        log.debug("[Attribute][POST definitions] Started (objectType: {}, category: {})...",
            objectType, requestDto.getCategory());

        validateObjectType(objectType);

        List<AttributeDefinitionResponseDto.CreatedAttributeDto> createdOrUpdated = new ArrayList<>();

        for (AttributeDefinitionRequestDto.AttributeItemDto item : requestDto.getAttributes()) {

            AttributeDataType dataType = AttributeDataType.fromString(item.getDataType());

            if (item.getValidations() != null && !item.getValidations().isEmpty()) {
                List<String> invalidKeys = AttributeValidationConfig
                    .getInvalidKeys(dataType, item.getValidations().keySet());

                if (!invalidKeys.isEmpty()) {
                    throw new IllegalArgumentException(String.format(
                        "Invalid validation keys %s for dataType '%s'. Allowed keys: %s",
                        invalidKeys,
                        item.getDataType(),
                        AttributeValidationConfig.getAllowedKeys(dataType)
                    ));
                }
            }

            Optional<Attribute> existing = attributeRepository
                .findByObjectTypeAndCategoryAndTechnicalName(
                    objectType,
                    requestDto.getCategory(),
                    item.getTechnicalName()
                );

            Attribute attribute = existing.orElse(new Attribute());
            attribute.setName(item.getName());
            attribute.setTechnicalName(item.getTechnicalName());
            attribute.setDataType(dataType.name());
            attribute.setObjectType(objectType);
            attribute.setCategory(requestDto.getCategory());
            attribute.setReadOnly(item.getReadOnly() != null && item.getReadOnly());

            if (item.getValidations() != null && !item.getValidations().isEmpty()) {
                attribute.setProperties(toJsonString(item.getValidations()));
            }

            Attribute saved = attributeRepository.save(attribute);

            createdOrUpdated.add(
                AttributeDefinitionResponseDto.CreatedAttributeDto.builder()
                    .attributeDefId(saved.getId())
                    .name(saved.getName())
                    .technicalName(saved.getTechnicalName())
                    .build()
            );
        }

        log.debug("[Attribute][POST definitions] Done — {} attributes saved.", createdOrUpdated.size());

        return AttributeDefinitionResponseDto.builder()
            .objectType(objectType)
            .category(requestDto.getCategory())
            .createdOrUpdated(createdOrUpdated)
            .build();
    }

    // -----------------------------------------------------------------------
    // POST #2 — Upsert attribute values for a specific object instance
    //
    // CHANGE 1: Accept both technicalName and displayName (name) as key
    // CHANGE 4: Return only pushed keys in response, not the full stored map
    // -----------------------------------------------------------------------

    /**
     * POST /v2/api/object-types/{objectType}/{objectId}/attribute-values
     *
     * Values map key can be either:
     *   - technicalName (camelCase): "numberOfFiles"
     *   - displayName (name column): "Number of Files"
     * Service resolves both — tries technicalName first, falls back to name.
     *
     * Response returns ONLY the keys pushed in this request mapped to their attribute_ids.
     * The full stored map is not returned.
     */
    @Transactional
    public AttributeValueResponseDto upsertAttributeValues(
        String objectType,
        Long objectId,
        AttributeValueRequestDto requestDto
    ) {
        log.debug("[Attribute][POST values] Started (objectType: {}, objectId: {}, category: {})...",
            objectType, objectId, requestDto.getCategory());

        validateObjectType(objectType);
        validateObjectExists(objectType, objectId);

        // --- Resolve each key to its Attribute definition ---
        // Accepts both technicalName and displayName (name) as key
        List<String> unknownAttributes = new ArrayList<>();
        Map<String, Attribute> resolvedAttributes = new HashMap<>();

        for (String key : requestDto.getValues().keySet()) {
            Optional<Attribute> found = resolveAttributeByKey(objectType, requestDto.getCategory(), key);
            if (found.isEmpty()) {
                unknownAttributes.add(key);
            } else {
                resolvedAttributes.put(key, found.get());
            }
        }

        if (!unknownAttributes.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "The following attributes %s are not defined for objectType '%s' under category '%s'. " +
                "Provide either the technicalName or the display name. " +
                "Create them first via POST /v2/api/object-types/%s/attributes.",
                unknownAttributes, objectType, requestDto.getCategory(), objectType
            ));
        }

        // --- Load existing stored map and merge new values ---
        Optional<AttributeValue> existing = attributeValueRepository
            .findByObjectTypeAndObjectId(objectType, objectId);

        Map<String, Object> currentMap = new HashMap<>();
        if (existing.isPresent()) {
            currentMap = fromJsonString(existing.get().getValue());
        }

        // Map to track only what was pushed in this request — for response (CHANGE 4)
        Map<String, Object> pushedValues = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : requestDto.getValues().entrySet()) {
            String key     = entry.getKey();
            Object rawValue = entry.getValue();
            Attribute attribute = resolvedAttributes.get(key);

            String attributeIdKey = String.valueOf(attribute.getId());
            currentMap.put(attributeIdKey, rawValue);
            pushedValues.put(attributeIdKey, rawValue);
        }

        // --- Save single row ---
        AttributeValue attributeValue = existing.orElse(
            AttributeValue.builder()
                .objectType(objectType)
                .objectId(objectId)
                .build()
        );
        attributeValue.setValue(toJsonString(currentMap));
        attributeValueRepository.save(attributeValue);

        log.debug("[Attribute][POST values] Done — {} values upserted for objectId {}.",
            pushedValues.size(), objectId);

        // CHANGE 4: Return only what was pushed, not the full stored map
        return AttributeValueResponseDto.builder()
            .objectType(objectType)
            .objectId(objectId)
            .category(requestDto.getCategory())
            .values(pushedValues)
            .build();
    }

    // -----------------------------------------------------------------------
    // GET — Retrieve attribute values grouped by category
    //
    // CHANGE 3: Include readOnly per attribute in the response
    // -----------------------------------------------------------------------

    /**
     * GET /v2/api/object-types/{objectType}/{objectId}/attribute-values
     *
     * Returns values grouped by category.
     * Each attribute entry now includes readOnly so the frontend can
     * control editability per field in JSON Forms — not hardcoded.
     */
    public AttributeViewResponseDto getAttributeValues(String objectType, Long objectId) {
        log.debug("[Attribute][GET values] Started (objectType: {}, objectId: {})...", objectType, objectId);

        validateObjectType(objectType);

        Optional<AttributeValue> storedRow = attributeValueRepository
            .findByObjectTypeAndObjectId(objectType, objectId);

        if (storedRow.isEmpty()) {
            return AttributeViewResponseDto.builder()
                .objectType(objectType)
                .objectId(objectId)
                .categories(List.of())
                .build();
        }

        Map<String, Object> valueMap = fromJsonString(storedRow.get().getValue());

        if (valueMap.isEmpty()) {
            return AttributeViewResponseDto.builder()
                .objectType(objectType)
                .objectId(objectId)
                .categories(List.of())
                .build();
        }

        Map<String, List<AttributeViewResponseDto.AttributeEntryDto>> categoryMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            Long attributeId;
            try {
                attributeId = Long.parseLong(entry.getKey());
            } catch (NumberFormatException e) {
                log.warn("[Attribute][GET values] Skipping non-numeric key '{}' in value map.", entry.getKey());
                continue;
            }

            Optional<Attribute> attributeOpt = attributeRepository.findById(attributeId);
            if (attributeOpt.isEmpty()) {
                log.warn("[Attribute][GET values] Attribute id {} not found — skipping.", attributeId);
                continue;
            }

            Attribute attribute = attributeOpt.get();
            String jsonSchemaType = AttributeDataType.fromString(attribute.getDataType()).toJsonSchemaType();

            // CHANGE 3: include readOnly from ATTRIBUTE.READ_ONLY column
            categoryMap.computeIfAbsent(attribute.getCategory(), k -> new ArrayList<>())
                .add(
                    AttributeViewResponseDto.AttributeEntryDto.builder()
                        .attributeId(attributeId)
                        .name(attribute.getName())
                        .technicalName(attribute.getTechnicalName())
                        .dataType(jsonSchemaType)
                        .readOnly(attribute.getReadOnly() != null && attribute.getReadOnly())
                        .value(entry.getValue())
                        .build()
                );
        }

        List<AttributeViewResponseDto.CategoryDto> categories = categoryMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                List<AttributeViewResponseDto.AttributeEntryDto> sortedAttributes = e.getValue().stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
                return AttributeViewResponseDto.CategoryDto.builder()
                    .category(e.getKey())
                    .attributes(sortedAttributes)
                    .build();
            })
            .toList();

        log.debug("[Attribute][GET values] Done — {} categories found for objectId {}.",
            categories.size(), objectId);

        return AttributeViewResponseDto.builder()
            .objectType(objectType)
            .objectId(objectId)
            .categories(categories)
            .build();
    }

    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    /**
     * CHANGE 1: Resolve attribute by either technicalName or displayName (name).
     * Tries technicalName first, then falls back to name column.
     */
    private Optional<Attribute> resolveAttributeByKey(String objectType, String category, String key) {
        Optional<Attribute> byTechnicalName = attributeRepository
            .findByObjectTypeAndCategoryAndTechnicalName(objectType, category, key);
        if (byTechnicalName.isPresent()) {
            return byTechnicalName;
        }
        return attributeRepository
            .findByObjectTypeAndCategoryAndName(objectType, category, key);
    }

    private void validateObjectType(String objectType) {
        if (!ALLOWED_OBJECT_TYPES.contains(objectType)) {
            throw new IllegalArgumentException(String.format(
                "Invalid objectType '%s'. Allowed values: %s", objectType, ALLOWED_OBJECT_TYPES
            ));
        }
    }

    private void validateObjectExists(String objectType, Long objectId) {
        boolean exists = switch (objectType) {
            case OBJECT_TYPE_TABLES     -> dataSourceTableRepository.existsById(objectId);
            case OBJECT_TYPE_DATASOURCE -> dataSourceRepository.existsById(objectId);
            case OBJECT_TYPE_ENTITIES   -> jpaEntityRepository.existsById(objectId);
            default -> false;
        };

        if (!exists) {
            throw new IllegalArgumentException(String.format(
                "No %s found with id %d. Please provide a valid object id.",
                objectType, objectId
            ));
        }
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("[Attribute] Failed to serialize to JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize attribute value to JSON.", e);
        }
    }

    private Map<String, Object> fromJsonString(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("[Attribute] Failed to deserialize JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
