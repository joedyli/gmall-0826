package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.search.vo.GoodsVO;
import com.atguigu.gmall.search.vo.SearchParamVO;
import com.atguigu.gmall.search.vo.SearchResponseVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        System.out.println(sourceBuilder.toString());
        return sourceBuilder;
    }

    /**
     * 解析结果集
     * @param response
     * @return
     */
    private SearchResponseVO parseResult(SearchResponse response){
        System.out.println(response);

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
            goodsVOS.add(JSON.parseObject(goodsVOJson, GoodsVO.class));
        }
        responseVO.setData(goodsVOS);

        // 解析聚合结果集获取品牌
        responseVO.setBrand(null);

        // 解析聚合结果集获取分类
        responseVO.setCategory(null);

        // 解析聚合结果集获取规格参数
        responseVO.setAttrs(null);

        System.out.println(responseVO.toString());
        return responseVO;
    }
}
