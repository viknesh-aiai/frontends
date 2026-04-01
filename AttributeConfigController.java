package com.socgen.bigdata.catalog.web.controller.v2.attribute;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.socgen.bigdata.catalog.models.dto.attribute.AttributeDefinitionRequestDto;
import com.socgen.bigdata.catalog.models.dto.attribute.AttributeDefinitionResponseDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v2/api/entity-types", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Attribute Configs", description = "GBTO — Manage dynamic attribute configurations for Tables, DataSource and Dataset")
@Slf4j
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('SCOPE_api.sgdata-catalog.attributes')")
@ApiResponses(value = {
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "500", description = "Internal Server Error"),
})
public class AttributeConfigController {

    private final AttributeService attributeService;

    @PostMapping(value = "/{entityType}/attribute-configs", consumes = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "upsertAttributeConfigs",
        summary = "Create or update attribute configurations for an entity type",
        description = "Upserts dynamic attribute configurations for a given entity type under a category. " +
            "If an attribute with the same technicalName already exists under the same entityType and category it will be updated. " +
            "entityType: Tables | DataSource | Dataset."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Attribute configs created or updated successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeDefinitionResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeDefinitionResponseDto> upsertAttributeConfigs(
        @Parameter(description = "Entity type: Tables | DataSource | Dataset", required = true)
        @PathVariable String entityType,
        @Valid @RequestBody AttributeDefinitionRequestDto requestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attributeService.upsertAttributeConfigs(entityType, requestDto));
    }
}
