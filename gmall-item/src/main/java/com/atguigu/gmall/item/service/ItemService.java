package com.atguigu.gmall.item.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.pms.vo.ItemGroupVO;
import com.atguigu.gmall.item.vo.ItemVO;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVO;
import com.atguigu.gmall.sms.vo.ItemSaleVO;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    public ItemVO queryItemBySkuId(Long skuId) {
        ItemVO itemVO = new ItemVO();

        // sku相关信息
        Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(skuId);
        SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
        if (skuInfoEntity == null) {
            return null;
        }
        itemVO.setSkuId(skuId);
        itemVO.setSkuTitle(skuInfoEntity.getSkuTitle());
        itemVO.setSkuSubTitle(skuInfoEntity.getSkuSubtitle());
        itemVO.setPrice(skuInfoEntity.getPrice());
        itemVO.setWeight(skuInfoEntity.getWeight());

        // 营销信息
        Resp<List<ItemSaleVO>> itemSaleResp = this.smsClient.queryItemSaleBySkuId(skuId);
        List<ItemSaleVO> itemSaleVOList = itemSaleResp.getData();
        itemVO.setSales(itemSaleVOList);

        // 库存信息，是否有货
        Resp<List<WareSkuEntity>> wareSkuResp = this.wmsClient.queryWareSkusBySkuId(skuId);
        List<WareSkuEntity> wareSkuEntities = wareSkuResp.getData();
        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
            itemVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
        }

        // sku图片
        Resp<List<SkuImagesEntity>> skuImagesResp = this.pmsClient.querySkuImagesBySkuId(skuId);
        List<SkuImagesEntity> skuImagesEntities = skuImagesResp.getData();
        itemVO.setImages(skuImagesEntities);

        // 品牌
        Resp<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(skuInfoEntity.getBrandId());
        BrandEntity brandEntity = brandEntityResp.getData();
        if (brandEntity != null) {
            itemVO.setBrandId(brandEntity.getBrandId());
            itemVO.setBrandName(brandEntity.getName());
        }

        // 分类
        Resp<CategoryEntity> categoryEntityResp = this.pmsClient.queryCategoryById(skuInfoEntity.getCatalogId());
        CategoryEntity categoryEntity = categoryEntityResp.getData();
        if (categoryEntity != null) {
            itemVO.setCategoryId(categoryEntity.getCatId());
            itemVO.setCategoryName(categoryEntity.getName());
        }

        // spu信息
        Long spuId = skuInfoEntity.getSpuId();
        Resp<SpuInfoEntity> spuInfoEntityResp = this.pmsClient.querySpuById(spuId);
        SpuInfoEntity spuInfoEntity = spuInfoEntityResp.getData();
        if (spuInfoEntity != null) {
            itemVO.setSpuId(spuInfoEntity.getId());
            itemVO.setSpuName(spuInfoEntity.getSpuName());
        }

        // 销售属性
        Resp<List<SkuSaleAttrValueEntity>> saleAttrResp = this.pmsClient.querySaleAttrBySpuId(spuId);
        List<SkuSaleAttrValueEntity> saleAttrValueEntities = saleAttrResp.getData();
        itemVO.setSaleAttrs(saleAttrValueEntities);

        // 组及组下规格参数
        Resp<List<ItemGroupVO>> listResp = this.pmsClient.queryItemGroupsBySpuIdAndCid(spuId, skuInfoEntity.getCatalogId());
        List<ItemGroupVO> itemGroupVOList = listResp.getData();
        itemVO.setGroups(itemGroupVOList);

        // spu描述信息
        Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.pmsClient.querySpuDescBySpuId(spuId);
        SpuInfoDescEntity spuInfoDescEntity = spuInfoDescEntityResp.getData();
        if (spuInfoDescEntity != null) {
            String[] image = StringUtils.split(spuInfoDescEntity.getDecript(), ",");
            itemVO.setDesc(Arrays.asList(image));
        }
        return itemVO;
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 10, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
        for (int i = 0; i < 1000; i++) {
            threadPoolExecutor.execute(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(Thread.currentThread().getName() + ": 自定义线程池执行任务");
            });
        }

//        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
//        executorService.scheduleAtFixedRate(() -> {
//            System.out.println(Thread.currentThread().getName() + ": 定时执行任务！" + new Date());
//        }, 5, 10, TimeUnit.SECONDS);
//        executorService.schedule(() -> {
//            System.out.println(Thread.currentThread().getName() + ": 定时执行任务！");
//        }, 20, TimeUnit.SECONDS);

        // 无线大小/固定大小/单个线程的线程池
//        ExecutorService executorService = Executors.newFixedThreadPool(3);
//        for (int i = 0; i < 10; i++) {
//            executorService.submit(() -> {
//                System.out.println(Thread.currentThread().getName() + ": 线程池执行任务！");
//            });
//        }

//        for (int i = 0; i < 10; i++) {
//        FutureTask<String> futureTask = new FutureTask<>(new MyCallable());
//        new Thread(futureTask).start();
////            System.out.println(futureTask.get());  // 阻塞
//        while (!futureTask.isDone()){ // 轮询
//            System.out.println("有结果了吗？");
//        }
//        System.out.println("对方同意了！");
//        System.in.read();
//        }

//        for (int i = 0; i < 10; i++) {
//            new Thread(() -> {
//                System.out.println(Thread.currentThread().getName() + ": 使用runnable匿名内部类初始化一个线程");
//            }).start();
//        }
    }
}

class MyCallable implements Callable<String> {
    @Override
    public String call() throws Exception {
        System.out.println(Thread.currentThread().getName() + ": 使用Callable初始化一个线程");
        return "zhang3";
    }
}

class MyRunnable implements Runnable{
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + ": 使用runnable初始化一个线程");
    }
}

class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + ": 使用thread初始化了一个线程");
    }
}

