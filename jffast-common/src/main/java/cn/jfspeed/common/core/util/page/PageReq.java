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
package cn.jfspeed.common.core.util.page;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

/**
 * @author jffast
 */
@Getter
@Setter
@Schema(title = "分页查询实体类")
public class PageReq<T>  {
    @Schema(description  = "当前页")
    private Integer pageNo = 1;
    @Schema(description  = "每页的数量")
    private Integer pageSize =10;
    @Schema(
            hidden = true
    )
    private String ascs;
    @Schema(hidden = true)
    private String descs;
    @Schema(hidden = true )
    private T queryBean;

    public PageReq() {

    }
    public PageReq(Integer pageNum, Integer pageSize) {
        this.pageNo = pageNum;
        this.pageSize = pageSize;
    }

    public <T> Page<T> getPage() {
        Page<T> page = new Page(Convert.toLong(this.getPageNo(), 1L), Convert.toLong(this.getPageSize(), 10L));

        return page;
    }

    public void copyQueryBean(Object target) {
        BeanUtils.copyProperties(this.queryBean, target);
    }

}
