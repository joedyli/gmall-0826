package com.atguigu.gmall.search;

import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.search.vo.GoodsVO;
import com.atguigu.gmall.search.vo.SearchAttrVO;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private ElasticsearchRestTemplate restTemplate;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallPmsClient pmsClient;

    @Test
    void importData() {

        this.restTemplate.createIndex(GoodsVO.class);
        this.restTemplate.putMapping(GoodsVO.class);

        Long pageNum = 1l;
        Long pageSize = 100l;
        // 分页查询spu
        do {
            QueryCondition condition = new QueryCondition();
            condition.setPage(pageNum);
            condition.setLimit(pageSize);
            Resp<List<SpuInfoEntity>> listResp = this.pmsClient.querySpuPage(condition);
            List<SpuInfoEntity> spuInfoEntities = listResp.getData();

            // 遍历spu查询spu下的sku
            spuInfoEntities.forEach(spuInfoEntity -> {
                Resp<List<SkuInfoEntity>> skuResp = this.pmsClient.querySkusBySpuId(spuInfoEntity.getId());
                List<SkuInfoEntity> skuInfoEntities = skuResp.getData();
                // 判断sku集合是否为空
                if (!CollectionUtils.isEmpty(skuInfoEntities)) {
                    // 把sku转化为goodsVO
                    List<GoodsVO> goodsVOs = skuInfoEntities.stream().map(skuInfoEntity -> {
                        GoodsVO goodsVO = new GoodsVO();
                        // 把sku转化为goodsVO
                        goodsVO.setSkuId(skuInfoEntity.getSkuId());
                        goodsVO.setTitle(skuInfoEntity.getSkuTitle());
                        goodsVO.setSale(null); // TODO
                        goodsVO.setPic(skuInfoEntity.getSkuDefaultImg());
                        goodsVO.setPrice(skuInfoEntity.getPrice().doubleValue());
                        // 根据brandId查询品牌
                        Long brandId = skuInfoEntity.getBrandId();
                        Resp<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(brandId);
                        BrandEntity brandEntity = brandEntityResp.getData();
                        if (brandEntity != null) {
                            goodsVO.setBrandId(brandId);
                            goodsVO.setBrandName(brandEntity.getName());
                        }
                        // 根据分类id查询分类
                        Long catalogId = skuInfoEntity.getCatalogId();
                        Resp<CategoryEntity> categoryEntityResp = this.pmsClient.queryCategoryById(catalogId);
                        CategoryEntity categoryEntity = categoryEntityResp.getData();
                        if (categoryEntity != null) {
                            goodsVO.setCategoryId(catalogId);
                            goodsVO.setCategoryName(categoryEntity.getName());
                        }
                        goodsVO.setCreateTime(spuInfoEntity.getCreateTime());
                        // 根据skuId查询库存信息
                        Resp<List<WareSkuEntity>> wareSkuResp = this.wmsClient.queryWareSkusBySkuId(skuInfoEntity.getSkuId());
                        List<WareSkuEntity> wareSkuEntities = wareSkuResp.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                            goodsVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
                        }
                        // 根据spuId查询检索属性
                        Resp<List<ProductAttrValueEntity>> attrValueResp = this.pmsClient.queryAttrValueBySpuId(spuInfoEntity.getId());
                        List<ProductAttrValueEntity> attrValueEntities = attrValueResp.getData();
                        if (!CollectionUtils.isEmpty(attrValueEntities)) {
                            List<SearchAttrVO> searchAttrVOS = attrValueEntities.stream().map(productAttrValueEntity -> {
                                SearchAttrVO searchAttrVO = new SearchAttrVO();
                                searchAttrVO.setAttrId(productAttrValueEntity.getAttrId());
                                searchAttrVO.setAttrName(productAttrValueEntity.getAttrName());
                                searchAttrVO.setAttrValue(productAttrValueEntity.getAttrValue());
                                return searchAttrVO;
                            }).collect(Collectors.toList());
                            goodsVO.setAttrs(searchAttrVOS);
                        }

                        return goodsVO;
                    }).collect(Collectors.toList());
                    // 批量导入es中
                    this.goodsRepository.saveAll(goodsVOs);
                }
            });

            pageSize = new Long(spuInfoEntities.size());
            pageNum++;
        } while (pageSize == 100);

    }

}
