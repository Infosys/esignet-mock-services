package io.mosip.compass.admin.service;



import io.mosip.compass.admin.dto.UserInfoDTO;
import io.mosip.compass.admin.dto.UserInfoResponseDTO;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

public interface UserInfoService {

    UserInfoResponseDTO createUserInfo(UserInfoDTO userInfoDTO);

    String deleteUserInfo(UUID id);

    UserInfoDTO getUserInfoByNationalUid(String nationalUid);

    List<UserInfoDTO> getAllUsers();

    String deleteMultipleUsers(List<UUID> userInfoIds);

    String testDataProviderPlugin(String individualId) throws Exception;
}