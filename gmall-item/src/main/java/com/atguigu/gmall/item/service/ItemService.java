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

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVO queryItemBySkuId(Long skuId) {
        ItemVO itemVO = new ItemVO();

        // sku相关信息
        CompletableFuture<SkuInfoEntity> skuFuture = CompletableFuture.supplyAsync(() -> {
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
            return skuInfoEntity;
        }, threadPoolExecutor);

        // 营销信息
        CompletableFuture<Void> salesFuture = CompletableFuture.runAsync(() -> {
            Resp<List<ItemSaleVO>> itemSaleResp = this.smsClient.queryItemSaleBySkuId(skuId);
            List<ItemSaleVO> itemSaleVOList = itemSaleResp.getData();
            itemVO.setSales(itemSaleVOList);
        }, threadPoolExecutor);

        // 库存信息，是否有货
        CompletableFuture<Void> storeFuture = CompletableFuture.runAsync(() -> {
            Resp<List<WareSkuEntity>> wareSkuResp = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareSkuResp.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
            }
        }, threadPoolExecutor);

        // sku图片
        CompletableFuture<Void> imageFuture = CompletableFuture.runAsync(() -> {
            Resp<List<SkuImagesEntity>> skuImagesResp = this.pmsClient.querySkuImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = skuImagesResp.getData();
            itemVO.setImages(skuImagesEntities);
        }, threadPoolExecutor);

        // 品牌
        CompletableFuture<Void> brandFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
            Resp<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(skuInfoEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResp.getData();
            if (brandEntity != null) {
                itemVO.setBrandId(brandEntity.getBrandId());
                itemVO.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        // 分类
        CompletableFuture<Void> categoryFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
            Resp<CategoryEntity> categoryEntityResp = this.pmsClient.queryCategoryById(skuInfoEntity.getCatalogId());
            CategoryEntity categoryEntity = categoryEntityResp.getData();
            if (categoryEntity != null) {
                itemVO.setCategoryId(categoryEntity.getCatId());
                itemVO.setCategoryName(categoryEntity.getName());
            }
        }, threadPoolExecutor);

        // spu信息
        CompletableFuture<Void> spuFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
            Long spuId = skuInfoEntity.getSpuId();
            Resp<SpuInfoEntity> spuInfoEntityResp = this.pmsClient.querySpuById(spuId);
            SpuInfoEntity spuInfoEntity = spuInfoEntityResp.getData();
            if (spuInfoEntity != null) {
                itemVO.setSpuId(spuInfoEntity.getId());
                itemVO.setSpuName(spuInfoEntity.getSpuName());
            }
        }, threadPoolExecutor);

        // 销售属性
        CompletableFuture<Void> attrFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
            Resp<List<SkuSaleAttrValueEntity>> saleAttrResp = this.pmsClient.querySaleAttrBySpuId(skuInfoEntity.getSpuId());
            List<SkuSaleAttrValueEntity> saleAttrValueEntities = saleAttrResp.getData();
            itemVO.setSaleAttrs(saleAttrValueEntities);
        }, threadPoolExecutor);

        // 组及组下规格参数
        CompletableFuture<Void> groupFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
            Resp<List<ItemGroupVO>> listResp = this.pmsClient.queryItemGroupsBySpuIdAndCid(skuInfoEntity.getSpuId(), skuInfoEntity.getCatalogId());
            List<ItemGroupVO> itemGroupVOList = listResp.getData();
            itemVO.setGroups(itemGroupVOList);
        }, threadPoolExecutor);

        // spu描述信息
        CompletableFuture<Void> descFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
            Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.pmsClient.querySpuDescBySpuId(skuInfoEntity.getSpuId());
            SpuInfoDescEntity spuInfoDescEntity = spuInfoDescEntityResp.getData();
            if (spuInfoDescEntity != null) {
                String[] image = StringUtils.split(spuInfoDescEntity.getDecript(), ",");
                itemVO.setDesc(Arrays.asList(image));
            }
        }, threadPoolExecutor);

        CompletableFuture.allOf(salesFuture, storeFuture, imageFuture, brandFuture, categoryFuture, spuFuture, attrFuture, groupFuture, descFuture).join();

        return itemVO;
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {

//        CompletableFuture.runAsync(() -> {
//            System.out.println("初始化CompletableFuture子任务！ runAsync");
//        }, Executors.newFixedThreadPool(3));
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("初始化CompletableFuture子任务！ supplyAsync");
//            int i = 1 / 0;
            return "hello CompletableFuture";
        });
        CompletableFuture<String> future1 = future.thenApplyAsync(t -> {
            System.out.println("================thenApplyAsync==================");
            System.out.println("上一个任务的返回结果集t: " + t);
            return "hello thenApplyAsync";
        }).thenCombineAsync(CompletableFuture.supplyAsync(() -> {
            System.out.println("================这是第二任务==================");
            return "hello CompletableFuture2";
        }), (t, u) -> {
            System.out.println("================这是一个新的任务==================");
            System.out.println("第一个任务的返回结果集t: " + t);
            System.out.println("第二个任务的返回结果集u: " + u);
            return "hello CompletableFuture2";
        });
//        future.thenAcceptAsync(t -> {
//            System.out.println("================thenAcceptAsync==================");
//            System.out.println("上一个任务的返回结果集t: " + t);
//        });
//        future.thenRunAsync(() -> {
//            System.out.println("================thenRunAsync=====================");
//        });


//                .whenCompleteAsync((t, u) -> {
//            System.out.println("================whenCompleteAsync===============");
//            System.out.println("上一个任务的返回结果集t: " + t);
//            System.out.println("上一个任务的异常u: " + u);
//        }).exceptionally(t -> {
//            System.out.println("==================exceptionally=================");
//            System.out.println("上一个任务的异常t: " + t);
//            return "hello exceptionally!";
//        }).handleAsync((t, u) -> {
//            System.out.println("================handleAsync-====================");
//            System.out.println("上一个任务的返回结果集t: " + t);
//            System.out.println("上一个任务的异常u: " + u);
//            return "hello handleAsync";
//        });

//        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 10, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
//        for (int i = 0; i < 1000; i++) {
//            threadPoolExecutor.execute(() -> {
//                try {
//                    Thread.sleep(500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                System.out.println(Thread.currentThread().getName() + ": 自定义线程池执行任务");
//            });
//        }

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

