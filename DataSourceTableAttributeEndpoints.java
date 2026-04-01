package com.socgen.bigdata.catalog.web.controller.v2.datasource;

// Add these imports to DataSourceTableController:
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueRequestDto;
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueResponseDto;
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeViewResponseDto;
// import com.socgen.bigdata.catalog.services.attribute.AttributeService;
// Add AttributeService to constructor injection.

// Add these two methods inside DataSourceTableController:

/*

    @PostMapping(value = "/{id}/attributes", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "upsertTableAttributeValues",
        summary = "Create or update attribute values for a data source table",
        description = "Pushes attribute values for a specific data source table. " +
            "Accepts both technicalName and display name as key. " +
            "Values are merged — existing keys not in the request are preserved."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Attribute values upserted successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeValueResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "Table not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeValueResponseDto> upsertTableAttributeValues(
        @Parameter(description = "Data source table id", required = true, example = "1")
        @PathVariable(name = "id") Long tableId,
        @Valid @RequestBody AttributeValueRequestDto requestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attributeService.upsertTableAttributeValues(tableId, requestDto));
    }

    @GetMapping(value = "/{id}/attributes", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getTableAttributeValues",
        summary = "Get attribute values grouped by category for a data source table",
        description = "Returns all stored attribute values for the given table grouped by category. " +
            "Each entry includes dataType and readOnly for JSON Forms rendering."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attribute values retrieved successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeViewResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Table not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeViewResponseDto> getTableAttributeValues(
        @Parameter(description = "Data source table id", required = true, example = "1")
        @PathVariable(name = "id") Long tableId
    ) {
        return ResponseEntity.ok(attributeService.getTableAttributeValues(tableId));
    }

*/
