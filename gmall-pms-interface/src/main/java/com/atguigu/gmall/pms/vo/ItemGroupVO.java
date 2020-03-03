package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.ProductAttrValueEntity;
import lombok.Data;

import java.util.List;

@Data
public class ItemGroupVO {

    private Long groupId;
    private String groupName;
    private List<ProductAttrValueEntity> baseAttrValues;
}
