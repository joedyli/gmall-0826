package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.search.vo.GoodsVO;
import com.atguigu.gmall.search.vo.SearchParamVO;
import com.atguigu.gmall.search.vo.SearchResponseAttrVO;
import com.atguigu.gmall.search.vo.SearchResponseVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVO search(SearchParamVO searchParam) {
        try {
            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, buildDsl(searchParam));
            SearchResponse response = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchResponseVO responseVO = parseResult(response);

            // 分页参数，需要从用户参数中获取
            responseVO.setPageNum(searchParam.getPageNum());
            responseVO.setPageSize(searchParam.getPageSize());
            return responseVO;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 根据用户发送的参数构建查询条件
     * @return
     */
    private SearchSourceBuilder buildDsl(SearchParamVO searchParam){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 1. 构建查询条件和过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        // 1.1. 构建匹配查询
        String keyword = searchParam.getKeyword();
        if (StringUtils.isEmpty(keyword)){
            // 打广告，放默认信息
            return null;
        }
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        // 1.2. 构建过滤条件
        // 1.2.1. 构建品牌过滤
        Long[] brandId = searchParam.getBrandId();
        if (brandId != null && brandId.length != 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        // 1.2.2. 构建分类的过滤
        Long[] categoryId = searchParam.getCategoryId();
        if (categoryId != null && categoryId.length != 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }

        // 1.2.3. 构建价格区间
        Double priceFrom = searchParam.getPriceFrom();
        Double priceTo = searchParam.getPriceTo();
        if (priceFrom != null || priceTo != null) { // 判断起始和终止价格是否为null
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom != null) {
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }

        // 1.2.4. 构建是否有货的过滤
        Boolean store = searchParam.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 1.2.5. 构建规格参数的嵌套过滤
        String[] props = searchParam.getProps();
        if (props != null && props.length != 0){
            for (String prop : props) { // 33:3000-4000-5000
                String[] attr = StringUtils.split(prop, ":");
                // 判断用户传递的参数是否合法
                if (attr == null || attr.length != 2){
                    continue;
                }

                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                boolQuery.must(QueryBuilders.termQuery("attrs.attrId", attr[0])); // 规格参数id
                boolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", StringUtils.split(attr[1], "-"))); // 规格参数值
                boolQueryBuilder.filter(QueryBuilders.nestedQuery("attrs", boolQuery, ScoreMode.None));
            }
        }

        // 2. 构建排序  order=1:desc  （0：得分 1：价格  2：销量 3：新品）
        String order = searchParam.getOrder();
        if (StringUtils.isNotBlank(order)){
            String[] sorts = StringUtils.split(order, ":");
            if (sorts != null && sorts.length == 2) {  // [1  desc]
                String sortFiled = "_score";
                switch (sorts[0]) {
                    case "1": sortFiled = "price"; break;
                    case "2": sortFiled = "sale"; break;
                    case "3": sortFiled = "createTime"; break;
                    default:
                        break;
                }
                sourceBuilder.sort(sortFiled, StringUtils.equals("desc", sorts[1]) ? SortOrder.DESC : SortOrder.ASC);
            }
        }

        // 3. 构建分页
        Integer pageNum = searchParam.getPageNum();
        Integer pageSize = searchParam.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 4. 构建高亮
        sourceBuilder.highlighter(new HighlightBuilder()
                .field("title")
                .preTags("<font style='color:red'>")
                .postTags("</font>"));

        // 5. 构建聚合
        // 5.1. 品牌的聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName")));

        // 5.2. 分类的聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));

        // 5.3. 规格参数嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrsAgg", "attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue")))
        );

        // 6. 添加结果集过滤，只包含商品列表所需要的字段即可（skuId，title，price，pic）
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "price", "pic"}, null);
//        System.out.println(sourceBuilder.toString());
        return sourceBuilder;
    }

    /**
     * 解析结果集
     * @param response
     * @return
     */
    private SearchResponseVO parseResult(SearchResponse response){
//        System.out.println(response);

        SearchResponseVO responseVO = new SearchResponseVO();

        SearchHits hits = response.getHits();
        responseVO.setTotal(hits.totalHits); // 总记录数

        // 解析hits获取查询记录
        SearchHit[] hitsHits = hits.getHits();
        List<GoodsVO> goodsVOS = new ArrayList<>();
        // 遍历hitsHits数组，把hitsHit转化成goodsVO对象，放入goodsVOS集合中
        for (SearchHit hitsHit : hitsHits) {
            // goodsVO中的字段，不需要每一个都去设置，因为页面在渲染商品时，只需要skuId title pic price
            String goodsVOJson = hitsHit.getSourceAsString();
            // 对_source进行反序列化
            GoodsVO goodsVO = JSON.parseObject(goodsVOJson, GoodsVO.class);
            // 获取高亮结果集，覆盖普通的标题
            HighlightField highlightField = hitsHit.getHighlightFields().get("title");
            Text fragments = highlightField.getFragments()[0];
            goodsVO.setTitle(fragments.string());
            goodsVOS.add(goodsVO);
        }
        responseVO.setData(goodsVOS);

        // 解析聚合结果集获取品牌
        Map<String, Aggregation> aggsMap = response.getAggregations().getAsMap();// 品牌 分类 规格参数
        // 获取品牌的聚合，并强转成可解析的品牌聚合
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggsMap.get("brandIdAgg");
        // 获取品牌聚合下的所有桶
        List<? extends Terms.Bucket> buckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            // 把桶的集合转化成json字符串["{id: 4, name: 尚硅谷}"]
            List<String> brandValues = buckets.stream().map(bucket -> {
                // 每一个桶需要转化成：{id: 4, name: 尚硅谷}
                Map<String, Object> map = new HashMap<>();
                Long brandId = bucket.getKeyAsNumber().longValue();
                map.put("id", brandId);
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) bucket.getAggregations().get("brandNameAgg");
                map.put("name", brandNameAgg.getBuckets().get(0).getKeyAsString());
                return JSON.toJSONString(map);
            }).collect(Collectors.toList());
            SearchResponseAttrVO brandVO = new SearchResponseAttrVO();
            brandVO.setAttrName("品牌");
            brandVO.setAttrValues(brandValues); // ["{id: 4, name: 尚硅谷}"]
            responseVO.setBrand(brandVO);
        }

        // 解析聚合结果集获取分类
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggsMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)) {
            List<String> categoryValues = categoryIdAggBuckets.stream().map(bucket -> {
                Map<String, Object> map = new HashMap<>();
                long categoryId = ((Terms.Bucket) bucket).getKeyAsNumber().longValue();
                map.put("id", categoryId);
                ParsedStringTerms categoryNameAgg = ((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                map.put("name", categoryNameAgg.getBuckets().get(0).getKeyAsString());
                return JSON.toJSONString(map);
            }).collect(Collectors.toList());
            SearchResponseAttrVO categoryAttrVO = new SearchResponseAttrVO();
            categoryAttrVO.setAttrName("分类");
            categoryAttrVO.setAttrValues(categoryValues);
            responseVO.setCategory(categoryAttrVO);
        }

        // 解析聚合结果集获取规格参数
        ParsedNested attrsAgg = (ParsedNested)aggsMap.get("attrsAgg");
        // 获取嵌套聚合中的规格参数id子聚合
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrsAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets(); // 获取attrIdAgg中的桶
        // 判断桶是否为空，如果不为空，把桶的集合转化为List<SearchResponseAttrVO>
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
            List<SearchResponseAttrVO> attrVOList = attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVO attrVO = new SearchResponseAttrVO();
                attrVO.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                attrVO.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(valueAggBuckets)){
                    List<String> attrValues = valueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    attrVO.setAttrValues(attrValues);
                }
                return attrVO;
            }).collect(Collectors.toList());
            responseVO.setAttrs(attrVOList);
        }

//        System.out.println(responseVO.toString());
        return responseVO;
    }
}
