package com.socgen.bigdata.catalog.services.attribute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socgen.bigdata.catalog.exceptions.InvalidInputException;
import com.socgen.bigdata.catalog.exceptions.ResourceNotFoundException;
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
import java.util.Collections;
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
    private static final String OBJECT_TYPE_DATASET    = "Dataset";
    private static final List<String> ALLOWED_OBJECT_TYPES =
        List.of(OBJECT_TYPE_TABLES, OBJECT_TYPE_DATASOURCE, OBJECT_TYPE_DATASET);

    private final AttributeRepository       attributeRepository;
    private final AttributeValueRepository  attributeValueRepository;
    private final DataSourceTableRepository dataSourceTableRepository;
    private final DataSourceRepository      dataSourceRepository;
    private final JpaEntityRepository       jpaEntityRepository;
    private final ObjectMapper              objectMapper;

    @Transactional
    public AttributeDefinitionResponseDto createOrUpdateAttributeDefinitions(
        String objectType,
        AttributeDefinitionRequestDto requestDto
    ) {
        log.debug("[Attribute][POST definitions] objectType: {}, category: {}", objectType, requestDto.getCategory());

        validateObjectType(objectType);

        List<AttributeDefinitionResponseDto.CreatedAttributeDto> createdOrUpdated = new ArrayList<>();

        for (AttributeDefinitionRequestDto.AttributeItemDto item : requestDto.getAttributes()) {
            AttributeDataType dataType;
            try {
                dataType = AttributeDataType.fromString(item.getDataType());
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException(
                    "Invalid dataType for attribute '" + item.getTechnicalName() + "'",
                    Map.of("dataType", Collections.singletonList(e.getMessage()))
                );
            }

            Attribute attribute;

            if (item.getAttributeId() != null) {
                attribute = attributeRepository.findById(item.getAttributeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Attribute with id " + item.getAttributeId() + " not found."
                    ));
            } else {
                attribute = attributeRepository
                    .findByObjectTypeAndCategoryAndTechnicalName(
                        objectType, requestDto.getCategory(), item.getTechnicalName()
                    )
                    .orElse(new Attribute());
            }

            attribute.setName(item.getName());
            attribute.setTechnicalName(item.getTechnicalName());
            attribute.setDataType(dataType.name());
            attribute.setObjectType(objectType);
            attribute.setCategory(requestDto.getCategory());
            attribute.setReadOnly(item.getReadOnly() != null && item.getReadOnly());

            Attribute saved = attributeRepository.save(attribute);

            createdOrUpdated.add(
                AttributeDefinitionResponseDto.CreatedAttributeDto.builder()
                    .attributeDefId(saved.getId())
                    .name(saved.getName())
                    .technicalName(saved.getTechnicalName())
                    .build()
            );
        }

        return AttributeDefinitionResponseDto.builder()
            .objectType(objectType)
            .category(requestDto.getCategory())
            .createdOrUpdated(createdOrUpdated)
            .build();
    }

    @Transactional
    public AttributeValueResponseDto upsertAttributeValues(
        String objectType,
        Long objectId,
        AttributeValueRequestDto requestDto
    ) {
        log.debug("[Attribute][POST values] objectType: {}, objectId: {}, category: {}",
            objectType, objectId, requestDto.getCategory());

        validateObjectType(objectType);
        validateObjectExists(objectType, objectId);

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
            throw new InvalidInputException(
                "Unknown attributes for objectType '" + objectType + "' under category '" + requestDto.getCategory() + "'",
                Map.of("unknownAttributes", unknownAttributes)
            );
        }

        // Validate each value matches the declared dataType
        List<String> typeErrors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : requestDto.getValues().entrySet()) {
            Attribute attribute = resolvedAttributes.get(entry.getKey());
            AttributeDataType dataType = AttributeDataType.fromString(attribute.getDataType());
            String error = validateValueType(dataType, entry.getValue(), entry.getKey());
            if (error != null) {
                typeErrors.add(error);
            }
        }

        if (!typeErrors.isEmpty()) {
            throw new InvalidInputException(
                "Value type validation failed",
                Map.of("typeErrors", typeErrors)
            );
        }

        Optional<AttributeValue> existing = attributeValueRepository
            .findByObjectTypeAndObjectId(objectType, objectId);

        Map<String, Object> currentMap = new HashMap<>();
        if (existing.isPresent()) {
            currentMap = fromJsonString(existing.get().getValue());
        }

        Map<String, Object> pushedValues = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : requestDto.getValues().entrySet()) {
            Attribute attribute = resolvedAttributes.get(entry.getKey());
            String attributeIdKey = String.valueOf(attribute.getId());
            currentMap.put(attributeIdKey, entry.getValue());
            pushedValues.put(attributeIdKey, entry.getValue());
        }

        AttributeValue attributeValue = existing.orElse(
            AttributeValue.builder()
                .objectType(objectType)
                .objectId(objectId)
                .build()
        );
        attributeValue.setValue(toJsonString(currentMap));
        attributeValueRepository.save(attributeValue);

        return AttributeValueResponseDto.builder()
            .objectType(objectType)
            .objectId(objectId)
            .category(requestDto.getCategory())
            .values(pushedValues)
            .build();
    }

    public AttributeViewResponseDto getAttributeValues(String objectType, Long objectId) {
        log.debug("[Attribute][GET values] objectType: {}, objectId: {}", objectType, objectId);

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
                log.warn("[Attribute][GET values] Skipping non-numeric key '{}'", entry.getKey());
                continue;
            }

            Optional<Attribute> attributeOpt = attributeRepository.findById(attributeId);
            if (attributeOpt.isEmpty()) {
                log.warn("[Attribute][GET values] Attribute id {} not found — skipping.", attributeId);
                continue;
            }

            Attribute attribute = attributeOpt.get();
            String jsonSchemaType = AttributeDataType.fromString(attribute.getDataType()).toJsonSchemaType();

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
                List<AttributeViewResponseDto.AttributeEntryDto> sorted = e.getValue().stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
                return AttributeViewResponseDto.CategoryDto.builder()
                    .category(e.getKey())
                    .attributes(sorted)
                    .build();
            })
            .toList();

        return AttributeViewResponseDto.builder()
            .objectType(objectType)
            .objectId(objectId)
            .categories(categories)
            .build();
    }

    /**
     * Validates that the pushed value matches the declared dataType.
     * Returns an error message if invalid, null if valid.
     *
     * INTEGER  — must be parseable as a whole number (Long)
     * FLOAT    — must be parseable as a decimal number (Double)
     * BOOLEAN  — must be true/false (Boolean or "true"/"false" string)
     * STRING   — anything accepted
     * ARRAY    — must be a List
     * OBJECT   — must be a Map
     */
    private String validateValueType(AttributeDataType dataType, Object value, String key) {
        if (value == null) {
            return null;
        }

        return switch (dataType) {
            case INTEGER -> {
                if (value instanceof Integer || value instanceof Long) yield null;
                try {
                    Long.parseLong(value.toString().trim());
                    yield null;
                } catch (NumberFormatException e) {
                    yield "Attribute '" + key + "': expected integer but got '" + value + "'.";
                }
            }
            case FLOAT -> {
                if (value instanceof Double || value instanceof Float || value instanceof Integer || value instanceof Long) yield null;
                try {
                    Double.parseDouble(value.toString().trim());
                    yield null;
                } catch (NumberFormatException e) {
                    yield "Attribute '" + key + "': expected float/number but got '" + value + "'.";
                }
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) yield null;
                String str = value.toString().trim().toLowerCase();
                if (str.equals("true") || str.equals("false")) yield null;
                yield "Attribute '" + key + "': expected boolean (true/false) but got '" + value + "'.";
            }
            case ARRAY -> {
                if (value instanceof List) yield null;
                yield "Attribute '" + key + "': expected array but got '" + value.getClass().getSimpleName() + "'.";
            }
            case OBJECT -> {
                if (value instanceof Map) yield null;
                yield "Attribute '" + key + "': expected object but got '" + value.getClass().getSimpleName() + "'.";
            }
            case STRING -> null;
        };
    }

    private Optional<Attribute> resolveAttributeByKey(String objectType, String category, String key) {
        Optional<Attribute> byTechnicalName = attributeRepository
            .findByObjectTypeAndCategoryAndTechnicalName(objectType, category, key);
        if (byTechnicalName.isPresent()) {
            return byTechnicalName;
        }
        return attributeRepository.findByObjectTypeAndCategoryAndName(objectType, category, key);
    }

    private void validateObjectType(String objectType) {
        if (!ALLOWED_OBJECT_TYPES.contains(objectType)) {
            throw new InvalidInputException(
                "Invalid objectType '" + objectType + "'",
                Map.of("objectType", Collections.singletonList("Allowed values: " + ALLOWED_OBJECT_TYPES))
            );
        }
    }

    private void validateObjectExists(String objectType, Long objectId) {
        boolean exists = switch (objectType) {
            case OBJECT_TYPE_TABLES     -> dataSourceTableRepository.existsById(objectId);
            case OBJECT_TYPE_DATASOURCE -> dataSourceRepository.existsById(objectId);
            case OBJECT_TYPE_DATASET    -> jpaEntityRepository.existsById(objectId);
            default -> false;
        };

        if (!exists) {
            throw new ResourceNotFoundException(objectType + " with id " + objectId + " not found.");
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
