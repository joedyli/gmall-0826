package com.atguigu.gmall.index.controller;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.vo.CategoryVO;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("index")
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping("cates")
    public Resp<List<CategoryEntity>> queryLvl1Category(){

        List<CategoryEntity> categoryEntities = this.indexService.queryLvl1Category();

        return Resp.ok(categoryEntities);
    }

    @GetMapping("/cates/{pid}")
    public Resp<List<CategoryVO>> queryLvl2WithSubByPid(@PathVariable("pid")Long pid){
        List<CategoryVO> categoryVOS = this.indexService.queryLvl2WithSubByPid(pid);
        return Resp.ok(categoryVOS);
    }

    @GetMapping("test/lock")
    public Resp<Object> testLock(){
        this.indexService.testLock();

        return Resp.ok(null);
    }

    @GetMapping("test/read")
    public Resp<Object> testRead(){
        this.indexService.testRead();

        return Resp.ok("读取成功了");
    }

    @GetMapping("test/write")
    public Resp<Object> testWrite(){
        this.indexService.testWrite();

        return Resp.ok("写入成功了");
    }


    @GetMapping("test/latch")
    public Resp<Object> testLatch() throws InterruptedException {
        this.indexService.testLatch();

        return Resp.ok("班长锁门。。。。");
    }

    @GetMapping("test/countdown")
    public Resp<Object> testCountDown(){
        this.indexService.testCountDown();

        return Resp.ok("出来一个学生。。。。");
    }

}
