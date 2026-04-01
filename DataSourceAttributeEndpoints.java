package com.socgen.bigdata.catalog.web.controller.v2.datasource;

// Add these imports to DataSourceController:
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueRequestDto;
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueResponseDto;
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeViewResponseDto;
// import com.socgen.bigdata.catalog.services.attribute.AttributeService;
// Add AttributeService to constructor injection.

// Add these two methods inside DataSourceController:

/*

    @PostMapping(value = "/{id}/attributes", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "upsertDataSourceAttributeValues",
        summary = "Create or update attribute values for a data source",
        description = "Pushes attribute values for a specific data source. " +
            "Accepts both technicalName and display name as key. " +
            "Values are merged — existing keys not in the request are preserved."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Attribute values upserted successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeValueResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "DataSource not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeValueResponseDto> upsertDataSourceAttributeValues(
        @Parameter(description = "Data source id", required = true, example = "1")
        @PathVariable(name = "id") Long datasourceId,
        @Valid @RequestBody AttributeValueRequestDto requestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attributeService.upsertDataSourceAttributeValues(datasourceId, requestDto));
    }

    @GetMapping(value = "/{id}/attributes", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDataSourceAttributeValues",
        summary = "Get attribute values grouped by category for a data source",
        description = "Returns all stored attribute values for the given data source grouped by category."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attribute values retrieved successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeViewResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "DataSource not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeViewResponseDto> getDataSourceAttributeValues(
        @Parameter(description = "Data source id", required = true, example = "1")
        @PathVariable(name = "id") Long datasourceId
    ) {
        return ResponseEntity.ok(attributeService.getDataSourceAttributeValues(datasourceId));
    }

*/
