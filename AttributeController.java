package com.socgen.bigdata.catalog.web.controller.v2.attribute;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.socgen.bigdata.catalog.models.dto.attribute.AttributeDefinitionRequestDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeDefinitionResponseDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeUpdateRequestDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueRequestDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueResponseDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeViewResponseDto;
import com.socgen.bigdata.catalog.services.attribute.AttributeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v2/api/object-types", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Dynamic Attributes", description = "GBTO — Manage dynamic attribute definitions and values for Tables, DataSource and Dataset")
@Slf4j
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('SCOPE_api.sgdata-catalog.attributes')")
@ApiResponses(value = {
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "500", description = "Internal Server Error"),
})
public class AttributeController {

    private final AttributeService attributeService;

    @PostMapping(value = "/{objectType}/attributes", consumes = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "createAttributeDefinitions",
        summary = "Create attribute definitions for an object type",
        description = "Creates new dynamic attributes for a given object type under a category. " +
            "Returns 400 if an attribute with the same technicalName already exists under the same objectType and category. " +
            "To update an existing attribute use PUT /{objectType}/attributes/{attributeId}. " +
            "objectType: Tables | DataSource | Dataset."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Attributes created successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeDefinitionResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request — attribute already exists or invalid input.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeDefinitionResponseDto> createAttributeDefinitions(
        @Parameter(description = "Object type: Tables | DataSource | Dataset", required = true)
        @PathVariable String objectType,
        @Valid @RequestBody AttributeDefinitionRequestDto requestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attributeService.createAttributeDefinitions(objectType, requestDto));
    }

    @PutMapping(value = "/{objectType}/attributes/{attributeId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "updateAttributeDefinition",
        summary = "Update an existing attribute definition",
        description = "Updates an existing attribute definition by its id. " +
            "All fields are replaced — name, technicalName, dataType, readOnly, category. " +
            "Since attribute values are stored by attributeId (not technicalName), " +
            "renaming technicalName does not break any stored values. " +
            "objectType: Tables | DataSource | Dataset."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attribute updated successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeDefinitionResponseDto.CreatedAttributeDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request — invalid input or attribute belongs to different objectType.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "Attribute not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeDefinitionResponseDto.CreatedAttributeDto> updateAttributeDefinition(
        @Parameter(description = "Object type: Tables | DataSource | Dataset", required = true)
        @PathVariable String objectType,
        @Parameter(description = "Attribute definition id returned from POST", required = true)
        @PathVariable Long attributeId,
        @Valid @RequestBody AttributeUpdateRequestDto requestDto
    ) {
        return ResponseEntity.ok(
            attributeService.updateAttributeDefinition(objectType, attributeId, requestDto)
        );
    }

    @PostMapping(value = "/{objectType}/{objectId}/attribute-values", consumes = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "upsertAttributeValues",
        summary = "Create or update attribute values for a specific object instance",
        description = "Pushes attribute values for a specific object instance. " +
            "Accepts both technicalName and display name as key. " +
            "Values are merged — existing keys not in the request are preserved."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Attribute values upserted successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeValueResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request — unknown attribute or type mismatch.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "Object instance not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeValueResponseDto> upsertAttributeValues(
        @Parameter(description = "Object type: Tables | DataSource | Dataset", required = true)
        @PathVariable String objectType,
        @Parameter(description = "Object instance PK", required = true)
        @PathVariable Long objectId,
        @Valid @RequestBody AttributeValueRequestDto requestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attributeService.upsertAttributeValues(objectType, objectId, requestDto));
    }

    @GetMapping(value = "/{objectType}/{objectId}/attribute-values")
    @Operation(
        operationId = "getAttributeValues",
        summary = "Get attribute values grouped by category for a specific object instance",
        description = "Returns all stored attribute values grouped by category. " +
            "Each entry includes dataType and readOnly so the frontend can control field editability."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attribute values retrieved successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeViewResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request — invalid objectType.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeViewResponseDto> getAttributeValues(
        @Parameter(description = "Object type: Tables | DataSource | Dataset", required = true)
        @PathVariable String objectType,
        @Parameter(description = "Object instance PK", required = true)
        @PathVariable Long objectId
    ) {
        return ResponseEntity.ok(attributeService.getAttributeValues(objectType, objectId));
    }
}
