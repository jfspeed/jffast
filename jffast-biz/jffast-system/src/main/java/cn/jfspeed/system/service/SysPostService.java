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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.system.domain.dto.SysPostQueryRequest;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysPost;
import cn.jfspeed.system.entity.SysUserPost;
import cn.jfspeed.system.mapper.SysPostMapper;
import cn.jfspeed.system.mapper.SysUserPostMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 岗位 服务实现
 *
 * @author jffast
 */
@Service
public class SysPostService extends BaseService<SysPostMapper, SysPost> {

    @Resource
    private SysUserPostMapper sysUserPostMapper;

    /**
     * 岗位分页列表
     */
    public PageRes<SysPost> page(SysPostQueryRequest query) {
        LambdaQueryWrapper<SysPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getPostCode()), SysPost::getPostCode, query.getPostCode());
        queryWrapper.like(StrUtil.isNotBlank(query.getPostName()), SysPost::getPostName, query.getPostName());
        queryWrapper.eq(query.getState() != null, SysPost::getState, query.getState());
        queryWrapper.orderByAsc(SysPost::getSort);
        Page<SysPost> page = this.page(query.getPage(), queryWrapper);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 判断岗位编码是否已存在
     */
    public boolean existsByCode(String postCode, Long id) {
        LambdaQueryWrapper<SysPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPost::getPostCode, postCode);
        queryWrapper.ne(id != null, SysPost::getId, id);
        long count = baseMapper.selectCount(queryWrapper);
        return count > 0;
    }

    /**
     * 岗位下拉：只列启用中的岗位，按 sort 升序。
     *
     * <p>停用岗位不出现在下拉里，但已绑定停用岗位的用户在编辑时仍需回显该绑定，
     * 由前端把「下拉结果 ∪ 详情回填项」取并集，否则编辑其它字段会顺手抹掉这条绑定。
     */
    public List<SysPost> listEnabledOptions() {
        LambdaQueryWrapper<SysPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPost::getState, 1);
        queryWrapper.orderByAsc(SysPost::getSort);
        return this.list(queryWrapper);
    }

    /** 该岗位当前的用户绑定数（命中 sys_user_post 主键 (post_id, user_id) 前缀） */
    public long countUserBindings(Long postId) {
        QueryWrapper<SysUserPost> query = new QueryWrapper<>();
        query.eq("post_id", postId);
        return sysUserPostMapper.selectCount(query);
    }

    /**
     * 删除岗位：仍被用户绑定则拒绝，返回错误文案；成功返回 null。
     *
     * <p>不做级联解绑：关联关系不得由单侧静默消失，且删掉的绑定无法恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public String deletePost(Long id) {
        if (id == null || this.getById(id) == null) {
            return "岗位不存在";
        }
        if (countUserBindings(id) > 0) {
            return "岗位已分配用户，不能删除";
        }
        this.removeById(id);
        return null;
    }
}
