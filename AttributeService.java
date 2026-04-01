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

    private static final String ENTITY_TYPE_TABLES     = "Tables";
    private static final String ENTITY_TYPE_DATASOURCE = "DataSource";
    private static final String ENTITY_TYPE_DATASET    = "Dataset";
    private static final List<String> ALLOWED_ENTITY_TYPES =
        List.of(ENTITY_TYPE_TABLES, ENTITY_TYPE_DATASOURCE, ENTITY_TYPE_DATASET);

    private final AttributeRepository       attributeRepository;
    private final AttributeValueRepository  attributeValueRepository;
    private final DataSourceTableRepository dataSourceTableRepository;
    private final DataSourceRepository      dataSourceRepository;
    private final JpaEntityRepository       jpaEntityRepository;
    private final ObjectMapper              objectMapper;

    @Transactional
    public AttributeDefinitionResponseDto upsertAttributeConfigs(
        String entityType,
        AttributeDefinitionRequestDto requestDto
    ) {
        log.debug("[AttributeConfig][POST] entityType: {}, category: {}", entityType, requestDto.getCategory());

        validateEntityType(entityType);

        List<AttributeDefinitionResponseDto.CreatedAttributeDto> createdOrUpdated = new ArrayList<>();

        for (AttributeDefinitionRequestDto.AttributeItemDto item : requestDto.getAttributes()) {

            AttributeDataType dataType = parseDataType(item.getDataType(), item.getTechnicalName());

            Optional<Attribute> existing = attributeRepository
                .findByObjectTypeAndCategoryAndTechnicalName(
                    entityType, requestDto.getCategory(), item.getTechnicalName()
                );

            Attribute attribute = existing.orElse(new Attribute());
            attribute.setName(item.getName());
            attribute.setTechnicalName(item.getTechnicalName());
            attribute.setDataType(dataType.name());
            attribute.setObjectType(entityType);
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
            .objectType(entityType)
            .category(requestDto.getCategory())
            .createdOrUpdated(createdOrUpdated)
            .build();
    }

    @Transactional
    public AttributeValueResponseDto upsertTableAttributeValues(Long tableId, AttributeValueRequestDto requestDto) {
        log.debug("[Attribute][POST table values] tableId: {}", tableId);
        if (!dataSourceTableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Table with id " + tableId + " not found.");
        }
        return upsertValues(ENTITY_TYPE_TABLES, tableId, requestDto);
    }

    @Transactional
    public AttributeValueResponseDto upsertDataSourceAttributeValues(Long datasourceId, AttributeValueRequestDto requestDto) {
        log.debug("[Attribute][POST datasource values] datasourceId: {}", datasourceId);
        if (!dataSourceRepository.existsById(datasourceId)) {
            throw new ResourceNotFoundException("DataSource with id " + datasourceId + " not found.");
        }
        return upsertValues(ENTITY_TYPE_DATASOURCE, datasourceId, requestDto);
    }

    @Transactional
    public AttributeValueResponseDto upsertDatasetAttributeValues(Long datasetId, AttributeValueRequestDto requestDto) {
        log.debug("[Attribute][POST dataset values] datasetId: {}", datasetId);
        if (!jpaEntityRepository.existsById(datasetId)) {
            throw new ResourceNotFoundException("Dataset with id " + datasetId + " not found.");
        }
        return upsertValues(ENTITY_TYPE_DATASET, datasetId, requestDto);
    }

    public AttributeViewResponseDto getTableAttributeValues(Long tableId) {
        log.debug("[Attribute][GET table values] tableId: {}", tableId);
        if (!dataSourceTableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Table with id " + tableId + " not found.");
        }
        return getAttributeValues(ENTITY_TYPE_TABLES, tableId);
    }

    public AttributeViewResponseDto getDataSourceAttributeValues(Long datasourceId) {
        log.debug("[Attribute][GET datasource values] datasourceId: {}", datasourceId);
        if (!dataSourceRepository.existsById(datasourceId)) {
            throw new ResourceNotFoundException("DataSource with id " + datasourceId + " not found.");
        }
        return getAttributeValues(ENTITY_TYPE_DATASOURCE, datasourceId);
    }

    public AttributeViewResponseDto getDatasetAttributeValues(Long datasetId) {
        log.debug("[Attribute][GET dataset values] datasetId: {}", datasetId);
        if (!jpaEntityRepository.existsById(datasetId)) {
            throw new ResourceNotFoundException("Dataset with id " + datasetId + " not found.");
        }
        return getAttributeValues(ENTITY_TYPE_DATASET, datasetId);
    }

    private AttributeValueResponseDto upsertValues(
        String entityType, Long entityId, AttributeValueRequestDto requestDto
    ) {
        List<String> unknownAttributes = new ArrayList<>();
        Map<String, Attribute> resolvedAttributes = new HashMap<>();

        for (String key : requestDto.getValues().keySet()) {
            Optional<Attribute> found = resolveAttributeByKey(entityType, requestDto.getCategory(), key);
            if (found.isEmpty()) {
                unknownAttributes.add(key);
            } else {
                resolvedAttributes.put(key, found.get());
            }
        }

        if (!unknownAttributes.isEmpty()) {
            throw new InvalidInputException(
                "Unknown attributes for entityType '" + entityType + "' under category '" + requestDto.getCategory() + "'",
                Map.of("unknownAttributes", unknownAttributes)
            );
        }

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
            .findByObjectTypeAndObjectId(entityType, entityId);

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
                .objectType(entityType)
                .objectId(entityId)
                .build()
        );
        attributeValue.setValue(toJsonString(currentMap));
        attributeValueRepository.save(attributeValue);

        return AttributeValueResponseDto.builder()
            .objectType(entityType)
            .objectId(entityId)
            .category(requestDto.getCategory())
            .values(pushedValues)
            .build();
    }

    private AttributeViewResponseDto getAttributeValues(String entityType, Long entityId) {
        Optional<AttributeValue> storedRow = attributeValueRepository
            .findByObjectTypeAndObjectId(entityType, entityId);

        if (storedRow.isEmpty()) {
            return AttributeViewResponseDto.builder()
                .objectType(entityType)
                .objectId(entityId)
                .categories(List.of())
                .build();
        }

        Map<String, Object> valueMap = fromJsonString(storedRow.get().getValue());

        if (valueMap.isEmpty()) {
            return AttributeViewResponseDto.builder()
                .objectType(entityType)
                .objectId(entityId)
                .categories(List.of())
                .build();
        }

        Map<String, List<AttributeViewResponseDto.AttributeEntryDto>> categoryMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            Long attributeId;
            try {
                attributeId = Long.parseLong(entry.getKey());
            } catch (NumberFormatException e) {
                log.warn("[Attribute][GET] Skipping non-numeric key '{}'", entry.getKey());
                continue;
            }

            Optional<Attribute> attributeOpt = attributeRepository.findById(attributeId);
            if (attributeOpt.isEmpty()) {
                log.warn("[Attribute][GET] Attribute id {} not found — skipping.", attributeId);
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
            .objectType(entityType)
            .objectId(entityId)
            .categories(categories)
            .build();
    }

    private AttributeDataType parseDataType(String dataType, String technicalName) {
        try {
            return AttributeDataType.fromString(dataType);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(
                "Invalid dataType for attribute '" + technicalName + "'",
                Map.of("dataType", Collections.singletonList(e.getMessage()))
            );
        }
    }

    private String validateValueType(AttributeDataType dataType, Object value, String key) {
        if (value == null) return null;
        return switch (dataType) {
            case INTEGER -> {
                if (value instanceof Integer || value instanceof Long) yield null;
                try { Long.parseLong(value.toString().trim()); yield null; }
                catch (NumberFormatException e) {
                    yield "Attribute '" + key + "': expected integer but got '" + value + "'.";
                }
            }
            case FLOAT -> {
                if (value instanceof Double || value instanceof Float
                    || value instanceof Integer || value instanceof Long) yield null;
                try { Double.parseDouble(value.toString().trim()); yield null; }
                catch (NumberFormatException e) {
                    yield "Attribute '" + key + "': expected float/number but got '" + value + "'.";
                }
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) yield null;
                String s = value.toString().trim().toLowerCase();
                if (s.equals("true") || s.equals("false")) yield null;
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

    private Optional<Attribute> resolveAttributeByKey(String entityType, String category, String key) {
        Optional<Attribute> byTechnicalName = attributeRepository
            .findByObjectTypeAndCategoryAndTechnicalName(entityType, category, key);
        if (byTechnicalName.isPresent()) return byTechnicalName;
        return attributeRepository.findByObjectTypeAndCategoryAndName(entityType, category, key);
    }

    private void validateEntityType(String entityType) {
        if (!ALLOWED_ENTITY_TYPES.contains(entityType)) {
            throw new InvalidInputException(
                "Invalid entityType '" + entityType + "'",
                Map.of("entityType", Collections.singletonList("Allowed values: " + ALLOWED_ENTITY_TYPES))
            );
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
