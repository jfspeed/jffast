/*
 * Copyright 2024-2026 jffast
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.jfspeed.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.system.domain.dto.SysUserProfileRequest;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.FileUrlSignService;
import cn.jfspeed.system.service.SysFileService;
import cn.jfspeed.system.service.SysUserService;

/**
 * 个人资料头像引用的越权回归（AC-17）。
 *
 * <p><b>IDOR 场景</b>：用户 A 提交 {@code avatar=file:<B 的文件 ID>}，
 * 若不校验归属，A 就能把 B 的文件挂到自己名下，进而拿到 B 文件的签名直链。
 *
 * <p>因此头像引用必须：① 形态是 {@code file:{id}}；② 文件属于本人（超管不限）。
 * 违反时**不得**更新资料，且 A 的资料 VO 里不能出现 B 文件的签名 URL。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserProfileFileRefTest {

    private static final long CURRENT_USER_ID = 7L;
    private static final long OTHER_FILE_ID = 999L;

    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @Mock
    private SysUserService service;

    @Mock
    private SysFileService sysFileService;

    @Mock
    private FileUrlSignService fileUrlSignService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private SysUserController controller;

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "sysFileService", sysFileService);
        ReflectionTestUtils.setField(controller, "fileUrlSignService", fileUrlSignService);
        ReflectionTestUtils.setField(controller, "fileStorageService", fileStorageService);
        when(loginPolicyProvider.getIfAvailable()).thenReturn(null);
        login = mockStatic(LoginUtil.class);
        // loginId() 走 LoginUtil.getLoginUser()（不是 getLoginUserId），两者都要备
        LoginUser loginUser = new LoginUser();
        loginUser.setId(CURRENT_USER_ID);
        loginUser.setUserName("tester");
        login.when(LoginUtil::getLoginUser).thenReturn(loginUser);
        login.when(LoginUtil::getLoginUserId).thenReturn(CURRENT_USER_ID);
        login.when(LoginUtil::isSuperAdmin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysUserProfileRequest request(String avatar) {
        SysUserProfileRequest req = new SysUserProfileRequest();
        req.setId(CURRENT_USER_ID);
        req.setRealName("赵亚辉");
        req.setAvatar(avatar);
        return req;
    }

    @Test
    @DisplayName("提交他人的文件引用：拒绝，不得更新资料")
    void otherUsersFileRefIsRejected() {
        // 服务层归属校验不通过（返回 null）
        when(sysFileService.getOwnedFile(OTHER_FILE_ID, CURRENT_USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.updateProfile(request("file:" + OTHER_FILE_ID)));

        assertEquals("文件不存在或无权使用", ex.getMessage());
        verify(service, never()).updateById(any());
    }

    @Test
    @DisplayName("提交格式非法的引用：拒绝（只接受 file:{id}）")
    void malformedRefIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.updateProfile(request("/upload/2026/11/other.png")));

        assertEquals("文件引用格式不正确", ex.getMessage());
        verify(service, never()).updateById(any());
    }

    @Test
    @DisplayName("提交裸签名直显地址：先归一成稳定引用再校验归属")
    void signedUrlIsNormalizedBeforeOwnershipCheck() {
        when(sysFileService.getOwnedFile(OTHER_FILE_ID, CURRENT_USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> controller.updateProfile(request("/sys/file/view/" + OTHER_FILE_ID + "?ticket=abc")));

        verify(sysFileService).getOwnedFile(OTHER_FILE_ID, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("提交本人文件引用：放行，落库的是稳定引用而非签名 URL")
    void ownFileRefIsAccepted() {
        when(sysFileService.getOwnedFile(100L, CURRENT_USER_ID)).thenReturn(new SysFile());
        when(service.getUser(anyLong(), any(), any())).thenReturn(new cn.jfspeed.system.entity.SysUser());

        controller.updateProfile(request("file:100"));

        org.mockito.ArgumentCaptor<cn.jfspeed.system.entity.SysUser> captor =
                org.mockito.ArgumentCaptor.forClass(cn.jfspeed.system.entity.SysUser.class);
        verify(service).updateById(captor.capture());
        assertEquals("file:100", captor.getValue().getAvatar(),
                "落库必须是稳定引用，不能是会过期的签名 URL");
    }
}
