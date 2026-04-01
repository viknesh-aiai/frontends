package com.socgen.bigdata.catalog.resources.v2;

// Add these imports to DatasetResource:
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueRequestDto;
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeValueResponseDto;
// import com.socgen.bigdata.catalog.models.dto.attribute.AttributeViewResponseDto;
// import com.socgen.bigdata.catalog.services.attribute.AttributeService;
// Add AttributeService to constructor injection.

// Add these two methods inside DatasetResource:

/*

    @PostMapping(value = "/{id}/attributes", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "upsertDatasetAttributeValues",
        summary = "Create or update attribute values for a dataset",
        description = "Pushes attribute values for a specific dataset. " +
            "Accepts both technicalName and display name as key. " +
            "Values are merged — existing keys not in the request are preserved."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Attribute values upserted successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeValueResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "Dataset not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeValueResponseDto> upsertDatasetAttributeValues(
        @Parameter(description = "Dataset id", required = true, example = "1")
        @PathVariable(name = "id") Long datasetId,
        @Valid @RequestBody AttributeValueRequestDto requestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attributeService.upsertDatasetAttributeValues(datasetId, requestDto));
    }

    @GetMapping(value = "/{id}/attributes", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDatasetAttributeValues",
        summary = "Get attribute values grouped by category for a dataset",
        description = "Returns all stored attribute values for the given dataset grouped by category."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attribute values retrieved successfully.",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = AttributeViewResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Dataset not found.",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE)),
    })
    public ResponseEntity<AttributeViewResponseDto> getDatasetAttributeValues(
        @Parameter(description = "Dataset id", required = true, example = "1")
        @PathVariable(name = "id") Long datasetId
    ) {
        return ResponseEntity.ok(attributeService.getDatasetAttributeValues(datasetId));
    }

*/
