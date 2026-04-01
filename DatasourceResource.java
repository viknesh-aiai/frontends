@PostMapping(value = "/{id}/attributes", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
@Operation(
    operationId = "upsertDatasetAttributeValues",
    summary = "Create or update attribute values for a dataset",
    description = "Pushes attribute values for a specific dataset."
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
