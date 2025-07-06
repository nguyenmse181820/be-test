package com.boeing.aircraftservice.services;

import com.boeing.aircraftservice.dtos.request.*;
import com.boeing.aircraftservice.dtos.response.AircraftResponseDTO;
import com.boeing.aircraftservice.dtos.response.AircraftTypeResponseDTO;
import com.boeing.aircraftservice.dtos.response.PagingResponse;
import com.boeing.aircraftservice.exception.BadRequestException;
import com.boeing.aircraftservice.exception.ElementExistException;
import com.boeing.aircraftservice.exception.ElementNotFoundException;
import com.boeing.aircraftservice.exception.UnchangedStateException;
import com.boeing.aircraftservice.mappers.AircraftMapper;
import com.boeing.aircraftservice.mappers.AircraftTypeMapper;
import com.boeing.aircraftservice.pojos.Aircraft;
import com.boeing.aircraftservice.pojos.AircraftType;
import com.boeing.aircraftservice.repositories.AircraftTypeRepository;
import com.boeing.aircraftservice.utils.AircraftTypeSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AircraftTypeServiceImpl implements AircraftTypeService {

    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftTypeMapper aircraftTypeMapper;
    private final AircraftMapper aircraftMapper;

    @Override
    public PagingResponse getAircraftsTypePaging(Integer currentPage, Integer pageSize) {
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftTypeRepository.findAll(pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircraftType paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircraftType paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO)
                                .toList())
                        .build();
    }

    @Override
    public PagingResponse getAircraftsTypeActive(Integer currentPage, Integer pageSize) {
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftTypeRepository.findAllByDeletedFalse(pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircraftType paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircraftType paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO)
                                .toList())
                        .build();
    }

    @Override
    public List<AircraftTypeResponseDTO> getAircraftsType() {
        return aircraftTypeRepository.findByDeletedIsFalse().stream().map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO).collect(Collectors.toList());
    }

    @Override
    public AircraftTypeResponseDTO findById(UUID aircraftTypeID) {
        AircraftType aircraft = aircraftTypeRepository.findAircraftTypeById(aircraftTypeID);
        if (aircraft == null) {
            throw new ElementNotFoundException("AircraftType can not found");
        }
        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraft);
    }

//    @Override
//    public AircraftTypeResponseDTO findByModel(SearchAircraftCodeRequest model) {
//        AircraftType aircraft = aircraftTypeRepository.findAircraftTypeByModel(model);
//        if (aircraft == null) {
//            throw new ElementNotFoundException("AircraftType can not found");
//        }
//        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraft);
//    }
//
//    @Override
//    public AircraftTypeResponseDTO findByManufacturer(SearchAircraftCodeRequest manufacturer) {
//        AircraftType aircraft = aircraftTypeRepository.findAircraftTypeByManufacturer(manufacturer);
//        if (aircraft == null) {
//            throw new ElementNotFoundException("AircraftType can not found");
//        }
//        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraft);
//    }

    @Override
    public AircraftTypeResponseDTO unDeleteAircraftType(UUID AircraftID) {
        AircraftType aircraft = aircraftTypeRepository.findAircraftTypeById(AircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("AircraftType can not found");
        }
        if (!aircraft.isDeleted()) {
            throw new UnchangedStateException("AircraftType is not deleted");
        }
        aircraft.setDeleted(false);
        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraftTypeRepository.save(aircraft));
    }

    @Override
    public AircraftTypeResponseDTO deleteAircraftType(UUID AircraftID) {
        AircraftType aircraft = aircraftTypeRepository.findAircraftTypeById(AircraftID);
        if (aircraft == null) {
            throw new ElementNotFoundException("AircraftType can not found");
        }
        if (aircraft.isDeleted()) {
            throw new UnchangedStateException("AircraftType was deleted yet");
        }
        aircraft.setDeleted(true);
        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraftTypeRepository.save(aircraft));
    }

    @Override
    public AircraftTypeResponseDTO createAircraftType(CreateAircraftTypeV2Request createAircraftTypeRequest) {
        if (createAircraftTypeRequest == null) {
            throw new BadRequestException("Create aircraft type request is required");
        }

        AircraftType aircraftType = aircraftTypeRepository.findAircraftTypeByModel(createAircraftTypeRequest.getAircraftType().getModel());

        if (aircraftType != null) {
            throw new ElementExistException("Aircraft type already exists");
        }

        AircraftType aircraftTypeNew = AircraftType.builder()
                .model(createAircraftTypeRequest.getAircraftType().getModel())
                .manufacturer(createAircraftTypeRequest.getAircraftType().getManufacturer())
                .seatMap((Map<String, Object>) createAircraftTypeRequest.getAircraftType().getSeatMap())
                .totalSeats(createAircraftTypeRequest.getAircraftType().getTotalSeats())
                .build();

        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraftTypeRepository.save(aircraftTypeNew));
    }

    @Override
    public AircraftTypeResponseDTO updateAircraftType(UpdateAircraftTypeV2Request updateAircraftTypeRequest, UUID aircraftID) {
        AircraftType aircraftType = aircraftTypeRepository.findAircraftTypeById(aircraftID);
        if (aircraftType == null) {
            throw new ElementNotFoundException("AircraftType not found");
        }

        if (!aircraftType.getModel().equals(updateAircraftTypeRequest.getAircraftType().getModel()) && StringUtils.hasText(updateAircraftTypeRequest.getAircraftType().getModel())) {
            AircraftType aircraftTypeExist = aircraftTypeRepository.findAircraftTypeByModel(updateAircraftTypeRequest.getAircraftType().getModel());
            if (aircraftTypeExist != null) {
                throw new ElementExistException("AircraftType already exists");
            }
            aircraftType.setModel(updateAircraftTypeRequest.getAircraftType().getModel());
        }

        if (StringUtils.hasText(updateAircraftTypeRequest.getAircraftType().getManufacturer())) {
            aircraftType.setManufacturer(updateAircraftTypeRequest.getAircraftType().getManufacturer());
        }

        if (updateAircraftTypeRequest.getAircraftType().getSeatMap() != null) {
            aircraftType.setSeatMap((Map<String, Object>) updateAircraftTypeRequest.getAircraftType().getSeatMap());
        }

        return aircraftTypeMapper.aircraftTypetoAircraftTypeResponseDTO(aircraftTypeRepository.save(aircraftType));
    }

    @Override
    public PagingResponse searchAircraftsType(Integer currentPage, Integer pageSize, String model, String manufacturer) {
        Pageable pageable;
        Specification<AircraftType> spec = Specification.where(null);

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        String searchCode = "";
        if (StringUtils.hasText(model)) {
            searchCode = model;
        }
        keys.add("model");
        values.add(searchCode);

        String searchModel = "";
        if (StringUtils.hasText(manufacturer)) {
            searchModel = manufacturer;
        }
        keys.add("manufacturer");
        values.add(searchModel);

        if(keys.size() == values.size()) {
            for(int i = 0; i < keys.size(); i++) {
                String field = keys.get(i);
                String value = values.get(i);
                Specification<AircraftType> newSpec = AircraftTypeSpecification.searchByField(field, value);
                if(newSpec != null) {
                    spec = spec.and(newSpec);
                }
            }
        }

        pageable = PageRequest.of(currentPage - 1, pageSize);

        var pageData = aircraftTypeRepository.findAll(spec, pageable);

        return !pageData.getContent().isEmpty() ? PagingResponse.builder()
                .code("Success")
                .message("Get all aircraftType paging successfully")
                .currentPage(currentPage)
                .elementPerPage(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .data(pageData.getContent().stream()
                        .map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO)
                        .toList())
                .build() :
                PagingResponse.builder()
                        .code("Failed")
                        .message("Get all aircraftType paging failed")
                        .currentPage(currentPage)
                        .elementPerPage(pageSize)
                        .totalElements(pageData.getTotalElements())
                        .totalPages(pageData.getTotalPages())
                        .data(pageData.getContent().stream()
                                .map(aircraftTypeMapper::aircraftTypetoAircraftTypeResponseDTO)
                                .toList())
                        .build();
    }

    @Override
    public List<AircraftResponseDTO> getAllAircraftByAircraftTypeID(UUID AircraftTypeID) {
        AircraftType aircraft = aircraftTypeRepository.findAircraftTypeById(AircraftTypeID);
        if (aircraft == null) {
            throw new ElementNotFoundException("AircraftType can not found");
        }
        if (aircraft.isDeleted()) {
            throw new UnchangedStateException("AircraftType was deleted");
        }
        return aircraft.getAircrafts().stream().map(aircraftMapper::aircrafttoAircraftResponseDTO).collect(Collectors.toList());
    }



}
