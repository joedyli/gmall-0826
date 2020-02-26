package com.atguigu.gmall.search.vo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "goods", type = "info", shards = 3, replicas = 2)
public class GoodsVO {

    // 商品列表
    @Id
    @Field(type = FieldType.Long, index = false)
    private Long skuId;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;
    @Field(type = FieldType.Double)
    private Double price;
    @Field(type = FieldType.Keyword, index = false)
    private String pic;  // 默认图片地址

    // 排序及筛选字段
    @Field(type = FieldType.Integer)
    private Integer sale; // 销量, 设置一个默认值
    @Field(type = FieldType.Date)
    private Date createTime;
    @Field(type = FieldType.Boolean)
    private Boolean store = false; // 是否有货

    @Field(type = FieldType.Long)
    private Long brandId;
    @Field(type = FieldType.Keyword)
    private String brandName;

    @Field(type = FieldType.Long)
    private Long categoryId;
    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Nested) // 嵌套类型
    private List<SearchAttrVO> attrs;
}
