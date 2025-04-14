package io.mosip.compass.admin.mapper;


import io.mosip.compass.admin.dto.UserInfoDTO;
import io.mosip.compass.admin.dto.UserInfoResponseDTO;
import io.mosip.compass.admin.entity.UserInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UserInfoMapper {

    @Mapping(target = "vcNum", ignore = true) // This is auto-generated
    @Mapping(target = "createdTimes", expression = "java(java.time.LocalDateTime.now())")
//    @Mapping(target = "faceImageColor", source = "faceImageColor", qualifiedByName = "base64ToBinary")
    @Mapping(target = "faceImageGrey", source = "faceImageColor", qualifiedByName = "convertColorBase64ToGreyBase64")
    UserInfo toEntity(UserInfoDTO dto);

//    @Mapping(target = "faceImageColor", source = "faceImageColor", qualifiedByName = "binaryToBase64")
//    @Mapping(target = "faceImageGrey", source = "faceImageGrey", qualifiedByName = "binaryToBase64")
    UserInfoDTO toDto(UserInfo entity);

    @Mapping(source = "userInfoId", target = "userInfoId")
    @Mapping(source = "nationalUid", target = "nationalUid")
    UserInfoResponseDTO toResponseDto(UserInfo entity);

    List<UserInfoDTO> toDtoList(List<UserInfo> entities);

    List<UserInfo> toEntityList(List<UserInfoDTO> dtos);


    // Named methods for conversion
    @Named("base64ToBinary")
    default byte[] base64ToBinary(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }
        return Base64.getDecoder().decode(base64String);
    }

    @Named("binaryToBase64")
    default String binaryToBase64(byte[] binaryData) {
        if (binaryData == null || binaryData.length == 0) {
            return null;
        }
        return Base64.getEncoder().encodeToString(binaryData);
    }

    @Named("convertColorBase64ToGreyBase64")
    default String convertColorBase64ToGreyBase64(String base64ColorImage) {
        if (base64ColorImage == null || base64ColorImage.isEmpty()) return null;

        try {
            // Extract MIME type
            String mimeType = base64ColorImage.substring(base64ColorImage.indexOf(":") + 1, base64ColorImage.indexOf(";"));
            // Get image data
            String base64Data = base64ColorImage.substring(base64ColorImage.indexOf(",") + 1);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // Convert to grayscale
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage colorImage = ImageIO.read(bis);

            BufferedImage grayImage = new BufferedImage(
                    colorImage.getWidth(),
                    colorImage.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY
            );

            Graphics g = grayImage.getGraphics();
            g.drawImage(colorImage, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(grayImage, mimeType.contains("png") ? "png" : "jpg", baos);
            byte[] greyBytes = baos.toByteArray();

            // Encode back to base64 with MIME
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(greyBytes);

        } catch (IOException e) {
            throw new RuntimeException("Failed to convert color image to grayscale Base64", e);
        }
    }
}