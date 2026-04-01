package com.socgen.bigdata.catalog.resources.v2;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.socgen.apibank.openapi.customizer.ProblemResponseReference;
import com.socgen.bigdata.catalog.enums.CollectionPatchAction;
import com.socgen.bigdata.catalog.enums.DatasetCompletionType;
import com.socgen.bigdata.catalog.enums.datasource.DataLakeAccessManagementEnvironment;
import com.socgen.bigdata.catalog.enums.sort.EntitySortEnum;
import com.socgen.bigdata.catalog.helpers.ExcelHelperConstant;
import com.socgen.bigdata.catalog.helpers.PaginationHelper;
import com.socgen.bigdata.catalog.models.dto.DatahubDistrictDto;
import com.socgen.bigdata.catalog.models.dto.DatasetCanRequestDataLakeWorkspaceDto;
import com.socgen.bigdata.catalog.models.dto.DatasetCompletionDto;
import com.socgen.bigdata.catalog.models.dto.DatasetDxDetailDto;
import com.socgen.bigdata.catalog.models.dto.DatasetVersionEnv;
import com.socgen.bigdata.catalog.models.dto.EntityDetailDto;
import com.socgen.bigdata.catalog.models.dto.EntityDto;
import com.socgen.bigdata.catalog.models.dto.ProjectsDto;
import com.socgen.bigdata.catalog.models.dto.UpdatedDatasetBusinessDataDto;
import com.socgen.bigdata.catalog.models.dto.VersionsDto;
import com.socgen.bigdata.catalog.models.dto.datalocalization.DataLocalizationDto;
import com.socgen.bigdata.catalog.models.dto.dataset.AddedDatasetFromExcelFileDto;
import com.socgen.bigdata.catalog.models.dto.dataset.DatasetDataSourceAddInformationDto;
import com.socgen.bigdata.catalog.models.dto.dataset.DatasetDataSourceDeployInformationDto;
import com.socgen.bigdata.catalog.models.dto.dataset.HasFilledApplicationAndStorageDto;
import com.socgen.bigdata.catalog.models.dto.datasource.DataSourceDto;
import com.socgen.bigdata.catalog.models.dto.datasource.LightDataSourceDto;
import com.socgen.bigdata.catalog.request.EntityDetailRequestBody;
import com.socgen.bigdata.catalog.services.CdoDomainService;
import com.socgen.bigdata.catalog.services.DataProjectService;
import com.socgen.bigdata.catalog.services.DxSearchService;
import com.socgen.bigdata.catalog.services.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RequestMapping("/v2/api/datasets")
@RestController
@NoArgsConstructor
@AllArgsConstructor
@Data
@Slf4j
@PreAuthorize("hasAuthority('SCOPE_api.sgdata-catalog.v2')")
@ApiResponses(
    value = {
        @ApiResponse(responseCode = "401", ref = ProblemResponseReference.UNAUTHORIZED_401),
        @ApiResponse(responseCode = "403", ref = ProblemResponseReference.FORBIDDEN_403),
    }
)
public class DatasetResource {

    @Autowired
    private EntityService entityService;

    @Autowired
    private DxSearchService dxSearchService;

    @Autowired
    private CdoDomainService cdoDomainService;

    @Value("${service.workflow.enabled}")
    private Boolean workflowEnabled;

    @Autowired
    private DataProjectService dataProjectService;

    @Operation(description = "Search all matching entities paginated")
    @GetMapping(produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EntityDto>> getEntities(
        @RequestParam(defaultValue = "alphabetical") EntitySortEnum sort,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(defaultValue = "0", name = "page") int pageSize,
        @Parameter(description = "Full-Text Value, example = Market credit application") @RequestParam(
            required = false
        ) String text,
        @Parameter(description = "Domains code, example = [L5_FEE,L3_DATA]") @RequestParam(
            name = "domains",
            required = false
        ) List<String> domains,
        @Parameter(description = "TAGS, example = [Risk,credit]") @RequestParam(name = "tags", required = false) List<
            String
        > tags,
        @Parameter(description = "expositions, example = [group,hidden]") @RequestParam(
            name = "expositions",
            required = false
        ) List<String> expositions,
        @Parameter(description = "lifecycles, example = [Released,Proposal,InProgress]") @RequestParam(
            name = "lifecycles",
            required = false
        ) List<String> lifecycles,
        @Parameter(description = "business-units, example = [GLBA,GBTO]") @RequestParam(
            name = "business-units",
            required = false
        ) List<String> busu,
        @Parameter(description = "businessphysicaldata, example = Organizational Unit") @RequestParam(
            name = "businessphysicaldata",
            required = false
        ) String businessphysicaldata,
        @Parameter(description = "people, example = [xyz@socgen.socgen,abc@sgib.socgen]") @RequestParam(
            name = "people",
            required = false
        ) List<String> contactEmails,
        @Parameter(description = "environments, example = [DEV,HML,PRD]") @RequestParam(
            name = "environments",
            required = false
        ) List<String> environments,
        @Parameter(description = "refresh-frequencies, example = [STREAMED,DAILY,WEEKLY]") @RequestParam(
            name = "refresh-frequencies",
            required = false
        ) List<String> refreshFrequencies,
        @Parameter(description = "ids, example = [1,2]") @RequestParam(name = "ids", required = false) List<String> ids,
        @Parameter(description = "projects, example = [1,2]") @RequestParam(name = "projects", required = false) List<
            Long
        > projectsId
    ) {
        Page<EntityDto> page = entityService.getDatasetsFromQuery(
            text,
            cdoDomainService.generateCdoDomainCodeRequest(domains),
            tags,
            expositions,
            lifecycles,
            environments,
            busu,
            businessphysicaldata,
            contactEmails,
            ids,
            refreshFrequencies,
            projectsId,
            PageRequest.of(pageSize, size, sort.getSort())
        );
        return PaginationHelper.responseEntityOk(page);
    }

    @Operation(description = "get the default version")
    @GetMapping(produces = APPLICATION_JSON_VALUE, value = "{id}/default")
    public ResponseEntity<DatasetDxDetailDto> getDefaultVersion(
        @Parameter(description = "Entity ID", required = true) @PathVariable(value = "id") Long id
    ) {
        return ResponseEntity.ok(entityService.findDxDefaultVersion(id));
    }

    @Operation(description = "get the detail of a version")
    @GetMapping(produces = APPLICATION_JSON_VALUE, value = "{id}/{version}")
    public ResponseEntity<DatasetDxDetailDto> getEntity(
        @Parameter(description = "origin id, example = 1", required = true) @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true,  schema = @Schema(allowableValues = {"v1"})) @PathVariable(value = "version") String version
    ) {
        return ResponseEntity.ok(entityService.findOneVersion(id, Long.parseLong(version.substring(1))));
    }

    @PreAuthorize("hasAuthority('api.sgdata-catalog_technical-operation')")
    @Operation(
        operationId = "computeBusinessRulesForAllDatasets",
        summary = "Compute business rules for all datasets",
        description = "compute business rules for all datasets"
    )
    @PostMapping(produces = APPLICATION_JSON_VALUE, value = "_compute-business-rules")
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the time in seconds it take to compute business rules.",
                content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = Long.class))
            ),
        }
    )
    public ResponseEntity<Long> computeBusinessRules() {
        return ResponseEntity.ok(entityService.computeBusinessRulesForAllDatasets());
    }

    @Operation(
        operationId = "checkHasAnyDataLakeDataSourcesById",
        summary = "Check if dataset by id contains any data lake data sources",
        description = "Return a boolean if a dataset by id contains any data lake data sources."
    )
    @GetMapping(produces = APPLICATION_JSON_VALUE, value = "{id}/{version}/check-has-data-sources")
    public ResponseEntity<Boolean> checkHasAnyDataLakeDataSourcesById(
        @Parameter(description = "Dataset origin id", required = true) @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true,  schema = @Schema(allowableValues = {"v1"})) @PathVariable(value = "version") String version
    ) {
        return ResponseEntity.ok(entityService.hasDatasetDataSources(id, Long.parseLong(version.substring(1))));
    }

    @Operation(
        operationId = "checkHasFilledApplicationAndStorage",
        summary = "Check if dataset has filled application and storage space",
        description = "Return a boolean if dataset has filled application and storage space."
    )
    @GetMapping(produces = APPLICATION_JSON_VALUE, value = "{id}/{version}/check-has-filled-application-and-storage")
    public ResponseEntity<HasFilledApplicationAndStorageDto> checkHasFilledApplicationAndStorage(
        @Parameter(description = "Dataset origin id", required = true) @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true,  schema = @Schema(allowableValues = {"v1"})) @PathVariable(value = "version") String version
    ) {
        return ResponseEntity.ok(entityService.hasFilledApplicationAndStorage(id, Long.parseLong(version.substring(1))));
    }

    @GetMapping(value = "/{id}/elementary-completion", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDatasetElementaryCompletionByIdAndVersion",
        summary = "Get dataset elementary completion",
        description = "Get dataset elementary completion with its details or not."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the dataset elementary completion with its details or not.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DatasetCompletionDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<DatasetCompletionDto> getDatasetElementaryCompletionByIdAndVersion(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(
            name = "id"
        ) Long datasetId,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            name = "dataset-version"
        ) Long datasetVersion,
        @Parameter(description = "With details (completed and missing properties)", example = "false") @RequestParam(
            name = "with-details",
            defaultValue = "false"
        ) Boolean withDetails
    ) {
        return ResponseEntity.ok(
            entityService.getCompletionByIdAndVersion(
                DatasetCompletionType.ELEMENTARY,
                datasetId,
                datasetVersion,
                withDetails
            )
        );
    }

    @GetMapping(value = "/{id}/advanced-completion", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDatasetAdvancedCompletionByIdAndVersion",
        summary = "Get dataset advanced completion",
        description = "Get dataset advanced completion with its details or not."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the dataset advanced completion with its details or not.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DatasetCompletionDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<DatasetCompletionDto> getDatasetAdvancedCompletionByIdAndVersion(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(
            name = "id"
        ) Long datasetId,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            name = "dataset-version"
        ) Long datasetVersion,
        @Parameter(description = "With details (completed and missing properties)", example = "false") @RequestParam(
            name = "with-details",
            defaultValue = "false"
        ) Boolean withDetails
    ) {
        return ResponseEntity.ok(
            entityService.getCompletionByIdAndVersion(
                DatasetCompletionType.ADVANCED,
                datasetId,
                datasetVersion,
                withDetails
            )
        );
    }

    @Operation(description = "get dataset default version")
    @GetMapping(value = "{id}/versions")
    public VersionsDto getVersionNumber(
        @Parameter(description = "origin id", required = true) @PathVariable(value = "id") Long id
    ) {
        return entityService.getVersions(id);
    }

    @Operation(description = "List all matching versions, this is used for entities autocomplete on data projects")
    @GetMapping(produces = APPLICATION_JSON_VALUE, value = "name")
    public ResponseEntity<List<EntityDto>> getEntitiesByName(
        @Parameter(description = "name of the entities") @RequestParam String text,
        @Parameter(description = "lifecycles") @RequestParam(name = "lifecycles", required = false) List<
            String
        > lifecycles
    ) {
        return ResponseEntity.ok(entityService.findVersionsByName(text, lifecycles));
    }

    @Operation(description = "get datahub district")
    @GetMapping(produces = APPLICATION_JSON_VALUE, value = "datahub-district")
    public ResponseEntity<List<DatahubDistrictDto>> getDatahubDistricts() {
        return ResponseEntity.ok(entityService.getDatahubDistricts());
    }

    @Operation(
        description = "Compute dataLocalization for all datasets",
        operationId = "computeDataLocalizationForAllDatasets",
        summary = "Compute dataLocalization for all datasets"
    )
    @PatchMapping(value = "/compute-data-localization")
    public List<DataLocalizationDto> computeDataLocalizationForAllDatasets() {
        return entityService.computeDataLocalizationForAllDatasets();
    }

    @Operation(description = "Get list dataset id by kear application id")
    @GetMapping("/kear-application-id")
    public List<Long> fetchListDatasetIdByKearAppId(
        @Parameter(description = "kear application id", required = true) @RequestParam(
            value = "kearAppId"
        ) String kearAppId
    ) {
        return entityService.getListDatasetIdByKearAppId(kearAppId);
    }

    @GetMapping(value = "/{id}/data-sources", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDatasetDataSourcesByIdAndVersion",
        summary = "Get paginated list of dataset data sources by id and version",
        description = "Return a paginated list of dataset data sources by an id and a version with a maximum of pagination elements equal to 100."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the paginated list of dataset data sources found by the id and the version.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = DataSourceDto.class))
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<Page<DataSourceDto>> getDatasetDataSourcesByIdAndVersion(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(name = "id") Long id,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            name = "version"
        ) Long version,
        @Parameter(description = "Response page number (start: 0)", name = "page") @RequestParam(
            name = "page",
            required = false,
            defaultValue = "0"
        ) int page,
        @Parameter(description = "Response size (max value: 100)", name = "size") @Max(100) @RequestParam(
            name = "size",
            required = false,
            defaultValue = "100"
        ) int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id"));

        return ResponseEntity.ok(entityService.getPageDataSources(id, version, pageable));
    }

    @GetMapping(value = "/{id}/data-sources/add", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDatasetDataSourceAddInformation",
        summary = "Get dataset data source add information",
        description = "Return the information for adding a data source to a dataset."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the information found for adding a data source to a dataset.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DatasetDataSourceAddInformationDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<DatasetDataSourceAddInformationDto> getDatasetDataSourceAddInformationByIdAndVersion(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(name = "id") Long id,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            name = "version"
        ) Long version
    ) {
        return ResponseEntity.ok(entityService.getDataSourceAddInformation(id, version));
    }

    @GetMapping(value = "/{id}/data-sources/deploy", produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "getDatasetDataSourceDeployInformation",
        summary = "Get dataset data source deploy information",
        description = "Return the information to determine whether a dataset can deploy a data source on a hosting platform."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the information found to determine whether a dataset can deploy a data source on a hosting platform.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DatasetDataSourceDeployInformationDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<DatasetDataSourceDeployInformationDto> getDatasetDataSourceDeployInformation(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(name = "id") Long id,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            name = "version"
        ) Long version,
        @Parameter(description = "Data source environment", required = true, example = "HML") @RequestParam(
            name = "dataSourceEnvironment"
        ) DataLakeAccessManagementEnvironment dataSourceEnvironment
    ) {
        return ResponseEntity.ok(entityService.getDataSourceDeployInformation(id, version, dataSourceEnvironment));
    }

    @GetMapping(value = "/{id}/projects", produces = APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Find projects by dataset id and version ",
        description = "Find projects by dataset id and dataset version."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the projects from sgi.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProjectsDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<ProjectsDto> getDataProjects(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            value = "version"
        ) Long version
    ) {
        return ResponseEntity.ok().body(dataProjectService.getDataSetProjects(id, version));
    }

    @Operation(description = "Add a dataset")
    @PostMapping
    public EntityDetailDto addADataset(@RequestBody EntityDetailRequestBody entityDetailRequestBody) {
        EntityDetailDto entityDetailDto = entityService.add(entityDetailRequestBody);
        if (entityDetailDto != null && workflowEnabled) {
            entityService.createWorkflowFillDataset(entityDetailDto);
        }
        return entityDetailDto;
    }

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(
        operationId = "addFromExcelFile",
        summary = "Add datasets from Excel file",
        description = "Create new datasets from an Excel file"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the added dataset ids",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = AddedDatasetFromExcelFileDto.class))
                )
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Bad Request.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Bad Request\", \"status\": 400, \"detail\": \"400 BAD_REQUEST \\\"Please upload an excel file!\\\"\"}"
                        ),
                    }
                )
            ),
            @ApiResponse(
                responseCode = "422",
                description = "Unprocessable Entity.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Unprocessable Entity\", \"status\": 422, \"detail\": \"422 UNPROCESSABLE_ENTITY \\\"Could not upload the file: <filename>!\\\"; nested exception is <exception cause>\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<List<AddedDatasetFromExcelFileDto>> addFromExcelFile(
        @RequestPart("file") MultipartFile file
    ) {
        String message;

        if (ExcelHelperConstant.hasExcelFormat(file)) {
            try {
                List<AddedDatasetFromExcelFileDto> addedDatasetFromExcelFileDtos = entityService.addFromExcelFile(file);

                return ResponseEntity.ok(addedDatasetFromExcelFileDtos);
            } catch (Exception e) {
                message = "Could not upload the file: " + file.getOriginalFilename() + "!";
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message, e);
            }
        }

        message = "Please upload an excel file!";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Operation(description = "create a version from default version")
    @PostMapping(value = "{id}/versions")
    public DatasetDxDetailDto newVersion(
        @Parameter(description = "origin id", required = true) @PathVariable(value = "id") Long id
    ) {
        return entityService.newVersion(id);
    }

    @Operation(description = "compute datasets eligibility")
    @PostMapping(produces = APPLICATION_JSON_VALUE, value = "/eligibility")
    public ResponseEntity<Map<Long, Boolean>> getDatasetsEligibility(
        @Parameter(
            description = "{<br/>&nbsp;&nbsp;\"303\" : 1 <br/>}"
        ) @RequestBody DatasetVersionEnv projectDatasetVersion
    ) {
        return ResponseEntity.ok(entityService.findDatasetsEligibility(projectDatasetVersion));
    }

    @Operation(description = "Edit a dataset")
    @PutMapping(value = "{id}/{version}")
    public DatasetDxDetailDto editDataset(
        @Parameter(description = "origin id", required = true) @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true,  schema = @Schema(allowableValues = {"v1"})) @PathVariable(value = "version") String version,
        @RequestBody EntityDetailRequestBody entityDetailDto
    ) {
        return entityService.putDx(entityDetailDto, id, Long.parseLong(version.substring(1)));
    }

    @Operation(description = "Patch a dataset")
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Data validation error"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
        }
    )
    @PatchMapping(value = "{id}/{version}")
    public Map<String, Object> patchDatasetVersion(
        @Parameter(description = "origin id", required = true) @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true,  schema = @Schema(allowableValues = {"v1"})) @PathVariable(value = "version") String version,
        @Parameter(
            description = "{<br/>" +
            "&nbsp;&nbsp;\"ownerDatasetPersonEmail\": \"\",<br/>" +
            "&nbsp;&nbsp;\"managerDatasetPersonEmail\":\"\",<br/>" +
            "&nbsp;&nbsp;\"managerBackupDatasetPersonsEmails\":<br/>" +
            "&nbsp;&nbsp;&nbsp;[<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;{<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"personToReplaceEmail\":\"\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"newPersonEmail\":\"\"<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;}<br/>" +
            "&nbsp;&nbsp;&nbsp;]<br/>" +
            "}"
        ) @RequestBody Map<String, Object> mapDx
    ) {
        Map<String, Object> entitydetail = entityService.patchDx(mapDx, id, Long.parseLong(version.substring(1)));
        return entitydetail;
    }

    @PatchMapping(value = "/can-request-data-lake-workspace", produces = APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update canRequest<ENV>DataLakeWorkspace properties",
        description = "Set the canRequestDevDataLakeWorkspace or canRequestProdDataLakeWorkspace property to true from the received list"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the updated datasets",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = EntityDto.class))
                )
            ),
            @ApiResponse(responseCode = "401", ref = ProblemResponseReference.UNAUTHORIZED_401),
            @ApiResponse(responseCode = "403", ref = ProblemResponseReference.FORBIDDEN_403),
        }
    )
    public ResponseEntity<List<EntityDto>> updateCanRequestDataLakeWorkspace(
        @RequestBody List<DatasetCanRequestDataLakeWorkspaceDto> datasetCanRequestDataLakeWorkspaceList
    ) {
        return ResponseEntity.ok(
            entityService.updateCanRequestDataLakeWorkspaceFromList(datasetCanRequestDataLakeWorkspaceList)
        );
    }

    @Operation(description = "Update all datasets completionRate")
    @PatchMapping(value = "/calculate-completion-rate")
    public ResponseEntity<Void> updateCompletionRateForAllEntities() {
        log.debug("API request to update completion rate for all datasets");
        entityService.updateCompletionRateForAllEntities();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-data-catalog-alert", "datasets.completionRate.updated");
        return ResponseEntity.noContent().headers(headers).build();
    }

    @PatchMapping(value = "/{id}/business-datas", produces = APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update dataset business datas",
        description = "Update dataset business datas from the received business data global id list."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the business data global ids not found and the updated dataset with its new business datas.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UpdatedDatasetBusinessDataDto.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<UpdatedDatasetBusinessDataDto> updateBusinessDatas(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            value = "version"
        ) Long version,
        @Parameter(description = "Business data action", required = true, example = "ADD") @RequestParam(
            value = "action"
        ) CollectionPatchAction action,
        @Parameter(
            description = "Business data global id list",
            required = true,
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = UUID.class))
            )
        ) @RequestBody List<UUID> businessDataGlobalIdList
    ) {
        if (!action.equals(CollectionPatchAction.ADD) && !action.equals(CollectionPatchAction.REPLACE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }

        return ResponseEntity.ok(
            entityService.updateBusinessDatasFromBusinessDataGlobalIdList(
                id,
                version,
                action,
                businessDataGlobalIdList.stream().map(UUID::toString).toList()
            )
        );
    }

    @PatchMapping(value = "/{id}/data-sources", produces = APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Add dataset data sources",
        description = "Add dataset data sources from the received data source id list."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Success.<br />Display the dataset data sources with its new data sources.",
                content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = LightDataSourceDto.class))
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found.",
                content = @Content(
                    mediaType = APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problem.class),
                    examples = {
                        @ExampleObject(
                            value = "{\"title\": \"Not Found\", \"status\": 404, \"detail\": \"Dataset with id '1' and version '1' not found!\"}"
                        ),
                    }
                )
            ),
        }
    )
    public ResponseEntity<Set<LightDataSourceDto>> updateDataSources(
        @Parameter(description = "Dataset id", required = true, example = "1") @PathVariable(value = "id") Long id,
        @Parameter(description = "Dataset version", required = true, example = "1") @RequestParam(
            value = "version"
        ) Long version,
        @Parameter(description = "Data source action", required = true, example = "ADD") @RequestParam(
            value = "action"
        ) CollectionPatchAction action,
        @Parameter(
            description = "Data source id list",
            required = true,
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = Long.class))
            )
        ) @RequestBody List<Long> dataSourceIdList
    ) {
        if (!action.equals(CollectionPatchAction.ADD) && !action.equals(CollectionPatchAction.REMOVE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }

        return ResponseEntity.ok(entityService.updateDataSources(id, version, action, dataSourceIdList));
    }
}
