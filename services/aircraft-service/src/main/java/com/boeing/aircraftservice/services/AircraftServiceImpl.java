package com.boeing.aircraftservice.services;

import com.boeing.aircraftservice.dtos.request.CreateAircraftRequest;
import com.boeing.aircraftservice.dtos.request.SearchAircraftCodeRequest;
import com.boeing.aircraftservice.dtos.request.UpdateAircraftRequest;
import com.boeing.aircraftservice.dtos.response.AircraftResponseDTO;
import com.boeing.aircraftservice.dtos.response.PagingResponse;
import com.boeing.aircraftservice.exception.BadRequestException;
import com.boeing.aircraftservice.exception.ElementExistException;
import com.boeing.aircraftservice.exception.ElementNotFoundException;
import com.boeing.aircraftservice.exception.UnchangedStateException;
import com.boeing.aircraftservice.mappers.AircraftMapper;
import com.boeing.aircraftservice.pojos.Aircraft;
import com.boeing.aircraftservice.pojos.AircraftType;
import com.boeing.aircraftservice.pojos.BaseEntity;
import com.boeing.aircraftservice.repositories.AircraftRepository;
import com.boeing.aircraftservice.repositories.AircraftTypeRepository;
import com.boeing.aircraftservice.utils.AircraftSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AircraftServiceImpl implements AircraftService {

    private final AircraftRepository aircraftRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftMapper aircraftMapper;

    @Override
    public PagingResponse getAircraftsPaging(Integer currentPage, Integer pageSize) {
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftRepository.findAll(pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircrafts paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircrafts paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                                .toList())
                        .build();
    }

    @Override
    public PagingResponse getAircraftsActive(Integer currentPage, Integer pageSize) {
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftRepository.findAllByDeletedFalse(pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircrafts paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircrafts paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                                .toList())
                        .build();
    }

    @Override
    public List<AircraftResponseDTO> getAircrafts() {
        return aircraftRepository.findByDeletedIsFalse().stream().map(aircraftMapper::aircrafttoAircraftResponseDTO).collect(Collectors.toList());
    }

    @Override
    public AircraftResponseDTO findById(UUID AircraftID) {
        Aircraft aircraft = aircraftRepository.findAircraftById(AircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("Aircraft can not found");
        }
        return aircraftMapper.aircrafttoAircraftResponseDTO(aircraft);
    }

    @Override
    public AircraftResponseDTO findByIdActive(UUID AircraftID) {
        Aircraft aircraft = aircraftRepository.findAircraftById(AircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("Aircraft can not found");
        }
        if (!aircraft.isDeleted()) {
            return aircraftMapper.aircrafttoAircraftResponseDTO(aircraft);
        }
        return null;
    }

    @Override
    public AircraftResponseDTO findByCode(SearchAircraftCodeRequest code) {
        Aircraft aircraft = aircraftRepository.findAircraftByCode(code.getCode());
        if (aircraft == null) {
            throw new ElementNotFoundException("Aircraft can not found");
        }
        return aircraftMapper.aircrafttoAircraftResponseDTO(aircraft);
    }

    @Override
    public AircraftResponseDTO unDeleteAircraft(UUID AircraftID) {
        Aircraft aircraft = aircraftRepository.findAircraftById(AircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("Aircraft can not found");
        }
        if (!aircraft.isDeleted()) {
            throw new UnchangedStateException("Aircraft is not deleted");
        }
        aircraft.setDeleted(false);
        return aircraftMapper.aircrafttoAircraftResponseDTO(aircraftRepository.save(aircraft));
    }

    @Override
    public AircraftResponseDTO deleteAircraft(UUID AircraftID) {
        Aircraft aircraft = aircraftRepository.findAircraftById(AircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("Aircraft can not found");
        }
        if (aircraft.isDeleted()) {
            throw new UnchangedStateException("Aircraft was deleted yet");
        }
        aircraft.setDeleted(true);
        return aircraftMapper.aircrafttoAircraftResponseDTO(aircraftRepository.save(aircraft));
    }

    @Transactional
    @Override
    public AircraftResponseDTO createAircraft(CreateAircraftRequest createAircraftRequest) {

        Aircraft aircraft = aircraftRepository.findAircraftByCode(createAircraftRequest.getCode());
        if (aircraft != null) {
            throw new ElementExistException("Aircraft already exists");
        }

        if (createAircraftRequest.getAircraftType() == null) {
            throw new BadRequestException("Create aircraft type request is required");
        }

        Aircraft aircraftNew = Aircraft.builder()
                .code(createAircraftRequest.getCode())
                .build();

        AircraftType aircraftType = aircraftTypeRepository.findAircraftTypeByModel(createAircraftRequest.getAircraftType().getModel());

        if (aircraftType != null) {
            aircraftNew.setAircraftType(aircraftType);
        } else {
            AircraftType aircraftTypeNew = AircraftType.builder()
                    .model(createAircraftRequest.getAircraftType().getModel())
                    .manufacturer(createAircraftRequest.getAircraftType().getManufacturer())
                    .seatMap((Map<String, Object>) createAircraftRequest.getAircraftType().getSeatMap())
                    .totalSeats(createAircraftRequest.getAircraftType().getTotalSeats())
                    .aircrafts(List.of(aircraftNew))
                    .build();

            aircraftNew.setAircraftType(aircraftTypeNew);
        }
        return aircraftMapper.aircrafttoAircraftResponseDTO(aircraftRepository.save(aircraftNew));
    }

    @Override
    public AircraftResponseDTO updateAircraft(UpdateAircraftRequest updateAircraftRequest, UUID aircraftID) {

        Aircraft aircraft = aircraftRepository.findAircraftById(aircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("Aircraft not found");
        }

        if (!aircraft.getCode().equals(updateAircraftRequest.getCode()) && StringUtils.hasText(updateAircraftRequest.getCode())) {
            Aircraft aircraftExisCode = aircraftRepository.findAircraftByCode(updateAircraftRequest.getCode());
            if (aircraftExisCode != null) {
                throw new ElementExistException("Aircraft already exists");
            }
            aircraft.setCode(updateAircraftRequest.getCode());
        }

        if (updateAircraftRequest.getAircraftType() == null) {
            throw new BadRequestException("Create aircraft type request is required");
        }

//        AircraftType aircraftType = aircraftTypeRepository.findAircraftTypeByModel(updateAircraftRequest.getAircraftType().getModel());
//
//        if (aircraftType != null) {
//            throw new ElementExistException("Aircraft type already exists");
//        }

        AircraftType aircraftType = aircraft.getAircraftType();

        if (!aircraftType.getModel().equals(updateAircraftRequest.getAircraftType().getModel()) && StringUtils.hasText(updateAircraftRequest.getAircraftType().getModel())) {
            AircraftType aircraftTypeExistModel = aircraftTypeRepository.findAircraftTypeByModel(updateAircraftRequest.getAircraftType().getModel());
            if (aircraftTypeExistModel != null) {
                throw new ElementExistException("AircraftTypeModel already exists");
            }
            aircraftType.setModel(updateAircraftRequest.getAircraftType().getModel());
        }

        if (StringUtils.hasText(updateAircraftRequest.getAircraftType().getManufacturer())) {
            aircraftType.setManufacturer(updateAircraftRequest.getAircraftType().getManufacturer());
        }

        if (updateAircraftRequest.getAircraftType().getSeatMap() != null) {
            aircraftType.setSeatMap((Map<String, Object>) updateAircraftRequest.getAircraftType().getSeatMap());
        }

        aircraft.setAircraftType(aircraftType);


        return aircraftMapper.aircrafttoAircraftResponseDTO(aircraftRepository.save(aircraft));
    }

    @Override
    public PagingResponse searchAircrafts(Integer currentPage, Integer pageSize, String code, String model, String manufacturer) {
        Pageable pageable;
        Specification<Aircraft> spec = Specification.where(null);

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        String searchCode = "";
        if (StringUtils.hasText(code)) {
            searchCode = code;
        }
        keys.add("code");
        values.add(searchCode);

        String searchModel = "";
        if (StringUtils.hasText(model)) {
            searchModel = model;
        }
        keys.add("model");
        values.add(searchModel);

        String searchManufacturer = "";
        if (StringUtils.hasText(manufacturer)) {
            searchManufacturer = manufacturer;
        }
        keys.add("manufacturer");
        values.add(searchManufacturer);

        if(keys.size() == values.size()) {
            for(int i = 0; i < keys.size(); i++) {
                String field = keys.get(i);
                String value = values.get(i);
                Specification<Aircraft> newSpec = AircraftSpecification.searchByField(field, value);
                if(newSpec != null) {
                    spec = spec.and(newSpec);
                }
            }
        }

        pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftRepository.findAll(spec, pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircrafts paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircrafts paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                                .toList())
                        .build();
    }

    @Override
    public PagingResponse searchAircraftsActive(Integer currentPage, Integer pageSize, String code, String model, String manufacturer) {
        Pageable pageable;
        Specification<Aircraft> spec = Specification.where(null);

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        String searchCode = "";
        if (StringUtils.hasText(code)) {
            searchCode = code;
        }
        keys.add("code");
        values.add(searchCode);

        String searchModel = "";
        if (StringUtils.hasText(model)) {
            searchModel = model;
        }
        keys.add("model");
        values.add(searchModel);

        String searchManufacturer = "";
        if (StringUtils.hasText(manufacturer)) {
            searchManufacturer = manufacturer;
        }
        keys.add("manufacturer");
        values.add(searchManufacturer);

        if(keys.size() == values.size()) {
            for(int i = 0; i < keys.size(); i++) {
                String field = keys.get(i);
                String value = values.get(i);
                Specification<Aircraft> newSpec = AircraftSpecification.searchByField(field, value);
                if(newSpec != null) {
                    spec = spec.and(newSpec);
                }
            }
        }

        pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftRepository.findAll(spec, pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircrafts paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircrafts paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .filter(BaseEntity::isDeleted)
                                .map(aircraftMapper::aircrafttoAircraftResponseDTO)
                                .toList())
                        .build();
    }

}
