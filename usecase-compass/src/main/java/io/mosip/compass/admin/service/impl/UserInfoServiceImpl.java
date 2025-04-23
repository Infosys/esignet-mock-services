package io.mosip.compass.admin.service.impl;

import io.mosip.compass.admin.dto.UserInfoDTO;
import io.mosip.compass.admin.dto.UserInfoResponseDTO;
import io.mosip.compass.admin.entity.UserInfo;
import io.mosip.compass.admin.exception.AdminServerException;
import io.mosip.compass.admin.mapper.UserInfoMapper;
import io.mosip.compass.admin.repository.DataProviderRepository;
import io.mosip.compass.admin.repository.UserInfoRepository;
import io.mosip.compass.admin.service.UserInfoService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.mosip.compass.admin.mapper.UserInfoMapper.log;

@Service
public class UserInfoServiceImpl implements UserInfoService {

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private DataProviderRepository dataProviderRepository;


    @Override
    @Transactional
    public UserInfoResponseDTO createUserInfo(UserInfoDTO userInfoDTO) {
        UserInfo userInfo = userInfoMapper.toEntity(userInfoDTO);
        UserInfo savedUserInfo = userInfoRepository.save(userInfo);
        return userInfoMapper.toResponseDto(savedUserInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoDTO getUserInfoByNationalUid(String nationalUid) {
        UserInfo userInfo = userInfoRepository.findByNationalUid(nationalUid)
                .orElseThrow(() -> new AdminServerException("UserInfo not found with National UID: " + nationalUid));
        return userInfoMapper.toDto(userInfo);
    }

    @Override
    public List<UserInfoDTO> getAllUsers() {
        List<UserInfo> userInfoList = userInfoRepository.findAllByOrderByCreatedTimesDesc();
        if(userInfoList.isEmpty()) {
            throw new AdminServerException("No users found!");
        }
        List<UserInfoDTO> userInfoDTOList = userInfoMapper.toDtoList(userInfoList);

        return userInfoDTOList;
    }

    @Override
    @Transactional
    public String deleteMultipleUsers(List<UUID> userInfoIds) {
        userInfoRepository.deleteAllByUserInfoIds(userInfoIds);
        return "Users with all userInfoIds deleted";
    }

    @Override
    @Transactional
    public String testDataProviderPlugin(String individualId) throws Exception {
        try {
            String queryString = "select * from user_info where national_uid=:id";
            if (individualId != null) {
                Map<String, Object> dataRecord = dataProviderRepository.fetchQueryResult(individualId,
                        queryString);
                JSONObject jsonResponse = new JSONObject(dataRecord);
                log.info("json object: " + jsonResponse);
                return  jsonResponse.toString();
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
//            throw new Exception("ERROR_FETCHING_DATA_RECORD_FROM_TABLE");
        }
        return "Failed";
    }

    @Override
    @Transactional
    public String deleteUserInfo(UUID id) {
        UserInfo userInfo = userInfoRepository.findById(id)
                .orElseThrow(() -> new AdminServerException("UserInfo not found with National UID: " + id));
        userInfoRepository.delete(userInfo);

        return "User deleted with userInfoId: " + id;
    }
}