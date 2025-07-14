package com.boeing.aircraftservice.controllers;

import com.boeing.aircraftservice.configurations.ApiVersion;
import com.boeing.aircraftservice.dtos.request.CreateAircraftTypeRequest;
import com.boeing.aircraftservice.dtos.request.CreateAircraftTypeV2Request;
import com.boeing.aircraftservice.dtos.request.UpdateAircraftTypeRequest;
import com.boeing.aircraftservice.dtos.request.UpdateAircraftTypeV2Request;
import com.boeing.aircraftservice.dtos.response.AircraftResponseDTO;
import com.boeing.aircraftservice.dtos.response.AircraftTypeResponseDTO;
import com.boeing.aircraftservice.dtos.response.ObjectResponse;
import com.boeing.aircraftservice.dtos.response.PagingResponse;
import com.boeing.aircraftservice.exception.BadRequestException;
import com.boeing.aircraftservice.exception.ElementExistException;
import com.boeing.aircraftservice.exception.ElementNotFoundException;
import com.boeing.aircraftservice.services.AircraftTypeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping(ApiVersion.V1 + "/aircraft-type")
@RestController
@RefreshScope
@RequiredArgsConstructor
@Slf4j
public class AircraftTypeController {

    private final AircraftTypeService aircraftTypeService;

    @Value("${application.default-current-page}")
    private int defaultCurrentPage;

    @Value("${application.default-page-size}")
    private int defaultPageSize;

    /**
     * Method get all aircraft-type
     *
     * @param currentPage currentOfThePage
     * @param pageSize numberOfElement
     * @return list or empty
     */
    @Operation(summary = "Get all aircraft-type", description = "Retrieves all aircraft-type, with optional pagination")
//    @PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasRole('STAFF')")
    @GetMapping("")
    public ResponseEntity<PagingResponse> getAllAircraftType(@RequestParam(value = "currentPage", required = false) Integer currentPage,
                                                             @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        int resolvedCurrentPage = (currentPage != null) ? currentPage : defaultCurrentPage;
        int resolvedPageSize = (pageSize != null) ? pageSize : defaultPageSize;
        PagingResponse results = aircraftTypeService.getAircraftsTypePaging(resolvedCurrentPage, resolvedPageSize);
        List<?> data = (List<?>) results.getData();
        return ResponseEntity.status(!data.isEmpty() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(results);
    }

    /**
     * Method get all aircraft-type have status is active
     *
     * @param currentPage currentOfThePage
     * @param pageSize numberOfElement
     * @return list or empty
     */
    @Operation(summary = "Get all aircraft-type active", description = "Retrieves all aircraft-type have status is active")
//    @PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasRole('STAFF')")
    @GetMapping("/active")
    public ResponseEntity<PagingResponse> getAllAircraftTypeActive(@RequestParam(value = "currentPage", required = false) Integer currentPage,
                                                               @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        int resolvedCurrentPage = (currentPage != null) ? currentPage : defaultCurrentPage;
        int resolvedPageSize = (pageSize != null) ? pageSize : defaultPageSize;
        PagingResponse results = aircraftTypeService.getAircraftsTypeActive(resolvedCurrentPage, resolvedPageSize);
        List<?> data = (List<?>) results.getData();
        return ResponseEntity.status(!data.isEmpty() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(results);
    }

    /**
     * Method get all aircraft-type non paging
     *
     * @return list or empty
     */
    @Operation(summary = "Get all aircraft-type non paging", description = "Retrieves all aircraft-type, without optional pagination")
//    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/non-paging/active")
    public ResponseEntity<ObjectResponse> getAllAircraftTypesNonPaging() {
        List<AircraftTypeResponseDTO> results = aircraftTypeService.getAircraftsTypeActive();
        return !results.isEmpty() ? ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "get all AircraftTypes non paging successfully", results)) :
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Failed", "get all AircraftTypes non paging failed", results));
    }

    @Operation(summary = "Get all aircraft-type non paging", description = "Retrieves all aircraft-type, without optional pagination")
//    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/non-paging")
    public ResponseEntity<ObjectResponse> getAllAircraftTypesNonPagingActive() {
        List<AircraftTypeResponseDTO> results = aircraftTypeService.getAircraftsType();
        return !results.isEmpty() ? ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "get all AircraftTypes non paging successfully", results)) :
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Failed", "get all AircraftTypes non paging failed", results));
    }

    /**
     * Method get all aircraft-type non paging
     *
     * @return list or empty
     */
    @Operation(summary = "Get all aircraft-type non paging", description = "Retrieves all aircraft-type, without optional pagination")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/air-crafts")
    public ResponseEntity<ObjectResponse> getAllAircraftByAircraftTypeID(@PathVariable UUID id) {
        List<AircraftResponseDTO> results = aircraftTypeService.getAllAircraftByAircraftTypeID(id);
        return !results.isEmpty() ? ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "get all Aircraft non paging successfully", results)) :
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Failed", "get all Aircraft non paging failed", results));
    }

    /**
     * Method search aircraft-type with name and sortBy
     *
     * @param currentPage currentOfThePage
     * @param pageSize    numberOfElement
     * @param model sortBy model with AircraftTypeType
     * @param manufacturer sortBy manufacturer with AircraftTypeType
     * @return list or empty
     */
    @Operation(summary = "Search aircraft-type", description = "Retrieves all aircraft-type are filtered by code, model and manufacturer")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<PagingResponse> searchAircraftType(@RequestParam(value = "currentPage", required = false) Integer currentPage,
                                                         @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                         @RequestParam(value = "model", required = false, defaultValue = "") String model,
                                                         @RequestParam(value = "manufacturer", required = false, defaultValue = "") String manufacturer) {
        int resolvedCurrentPage = (currentPage != null) ? currentPage : defaultCurrentPage;
        int resolvedPageSize = (pageSize != null) ? pageSize : defaultPageSize;

        PagingResponse results = aircraftTypeService.searchAircraftsType(resolvedCurrentPage, resolvedPageSize, model, manufacturer);
        List<?> data = (List<?>) results.getData();
        return ResponseEntity.status(!data.isEmpty() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(results);
    }

    /**
     * Method get AircraftType by AircraftType id
     *
     * @param id idOfAircraftType
     * @return list or empty
     */
    @Operation(summary = "Get AircraftType by AircraftType id", description = "Retrieves AircraftType by AircraftType id")
    @PreAuthorize("hasRole('USER') or hasRole('STAFF') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ObjectResponse> getAircraftTypeByID(@PathVariable("id") UUID id) {
        AircraftTypeResponseDTO aircraftType = aircraftTypeService.findById(id);
        return aircraftType != null ?
                ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "Get AircraftType by ID successfully", aircraftType)) :
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Get AircraftType by ID failed", null));
    }

    /**
     * Method create AircraftType
     *
     * @param createAircraftTypeRequest param basic for AircraftType
     * @return AircraftType or null
     */
    @Operation(summary = "Create AircraftType", description = "Create AircraftType")
//    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("")
    public ResponseEntity<ObjectResponse> createAircraftType(@Valid @RequestBody CreateAircraftTypeV2Request createAircraftTypeRequest) {
        try {
            AircraftTypeResponseDTO aircraftType = aircraftTypeService.createAircraftType(createAircraftTypeRequest);
            return ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "Create AircraftType successfully", aircraftType));
        } catch (BadRequestException e) {
            log.error("Error creating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", e.getMessage(), null));
        } catch (ElementExistException e) {
            log.error("Error creating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error creating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Create AircraftType failed", null));
        }
    }

    /**
     * Method update AircraftType
     *
     * @param updateAircraftTypeRequest param basic for AircraftType
     * @return AircraftType or null
     */
    @Operation(summary = "Update AircraftType", description = "Update AircraftType")
//    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ObjectResponse> updateAircraftType(@PathVariable("id") UUID id, @RequestBody UpdateAircraftTypeV2Request updateAircraftTypeRequest) {
        try {
            AircraftTypeResponseDTO aircraftType = aircraftTypeService.updateAircraftType(updateAircraftTypeRequest, id);
            if (aircraftType != null) {
                return ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "Update AircraftTypes successfully", aircraftType));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Update AircraftTypes failed. AircraftTypes is null", null));
        } catch (BadRequestException e) {
            log.error("Error creating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", e.getMessage(), null));
        } catch (ElementExistException e) {
            log.error("Error while updating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", e.getMessage(), null));
        } catch (ElementNotFoundException e) {
            log.error("Error while updating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Update AircraftType failed. AircraftType not found", null));
        } catch (Exception e) {
            log.error("Error updating AircraftTypes", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Update AircraftTypes failed", null));
        }
    }

    /**
     * Method restore AircraftType by AircraftType id set delete = true
     *
     * @param id idOfAircraftType
     * @return AircraftType or null
     */
    @Operation(summary = "Delete AircraftType", description = "Delete AircraftType by AircraftType id set delete = true")
//    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ObjectResponse> deleteAircraftTypeByID(@PathVariable("id") UUID id) {
        try {
            AircraftTypeResponseDTO aircraftType = aircraftTypeService.deleteAircraftType(id);
            if(aircraftType != null) {
                return ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "Delete AircraftTypes successfully", aircraftType));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Delete AircraftTypes failed", null));
        } catch (ElementNotFoundException e) {
            log.error("Error while updating AircraftTypes", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Update AircraftType failed" + e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deleting AircraftTypes", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Delete AircraftTypes failed", null));
        }
    }

    /**
     * Method restore AircraftType by AircraftType id set delete = false
     *
     * @param id idOfAircraftType
     * @return AircraftType or null
     */
    @Operation(summary = "Restore AircraftType", description = "Restore AircraftType by AircraftType id set delete = false")
//    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @PutMapping("/{id}/restore")
    public ResponseEntity<ObjectResponse> unDeleteAircraftTypeByID(@PathVariable("id") UUID id) {
        try {
            AircraftTypeResponseDTO aircraftType = aircraftTypeService.unDeleteAircraftType(id);
            if(aircraftType != null) {
                return ResponseEntity.status(HttpStatus.OK).body(new ObjectResponse("Success", "UnDelete AircraftType successfully", aircraftType));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "AircraftType is null", null));
        } catch (ElementNotFoundException e) {
            log.error("Error while updating AircraftType", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Update AircraftType failed" + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ObjectResponse("Fail", "Undelete AircraftType failed", null));
        }
    }

}
