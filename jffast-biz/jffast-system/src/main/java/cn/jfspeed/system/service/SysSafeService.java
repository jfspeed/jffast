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
package cn.jfspeed.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.system.entity.SysSafe;
import cn.jfspeed.system.mapper.SysSafeMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


/**
 * @author jffast
 */
@Service
public class SysSafeService extends BaseService<SysSafeMapper, SysSafe> {

    /**
     * 设置密码是否过了有效*
     */
    public void getPwdCycle(Map<String,Object> map, LocalDateTime updatePwdTime){
        List<SysSafe> safeList = baseMapper.selectList(Wrappers.emptyWrapper());
        if (safeList.isEmpty()) {
            // 未配置安全策略：按「未过期」处理，避免登录被误判为密码过期
            map.put("pwdCycle", false);
            map.put("idleTimeSetting", null);
            return;
        }
        SysSafe safe = safeList.get(0);
        Integer pwdCycle = safe.getPwdCycle();
        if (updatePwdTime != null){
            // 方向必须是从上次改密时间到现在；反过来会得到负值，永远不会判定过期
            long days = Duration.between(updatePwdTime, LocalDateTime.now()).toDays();
            map.put("pwdCycle", pwdCycle != null && pwdCycle > 0 && days >= pwdCycle);
        }else {
            map.put("pwdCycle",true);
        }
        map.put("idleTimeSetting",safe.getIdleTimeSetting());
    }

}
