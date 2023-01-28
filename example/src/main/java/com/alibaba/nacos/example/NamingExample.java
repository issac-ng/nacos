/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.example;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.NamingEvent;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Nacos naming example.
 *
 * @author nkorange
 */
public class NamingExample {
    /**
     * nacos client 完成服务的注册，发现，取消注册，订阅请按照：step1,step2,step3,step4 依次来看
     * (在step1、step2、step3、step4这四个步骤，在我们导入spring cloud alibaba的起步依赖就会找到 配置文件 spring_factories
     * 然后加载配置文件，找到配置类中的 nacosAutoServiceRegistration 返回类型是NacosAutoServiceRegistration 该类中有一个 register() 方法就是帮我们把服务自动注册到注册中心，我们就不用手写step1,step2,step3,step4 这些步骤
     * spring cloud 为服务的注册和发现对外提供了接口 org.springframework.cloud.client.serviceregistry  nacos只需要实现这些接口就可以了
     *  com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration,类中的方法NacosAutoServiceRegistration())
     * 集群模式下Distro协议数据同步
     * 1、集群节点的互相发现：com.alibaba.nacos.core.cluster.ServerMemberManager.ServerMemberManager
     * 2、启动后开始分区数据定时校验和启动加载全量快照数据：com.alibaba.nacos.core.distributed.distro.DistroProtocol.DistroProtocol
     * 3、运行过程中的增量数据同步：节点数据发生变更后进行同步
     *      比如：有服务注册后 在nacos server端发生数据同步 入口 com.alibaba.nacos.naming.controllers.InstanceController.register
     * @param args
     * @throws NacosException
     */
    public static void main(String[] args) throws NacosException {

        Properties properties = new Properties();
/*   nacos上原来的代码     properties.setProperty("serverAddr", System.getProperty("serverAddr"));
        properties.setProperty("namespace", System.getProperty("namespace"));*/
        /*把源码上的serverAddr和namespace写死*/
        properties.setProperty("serverAddr","127.0.0.1:8848");
        properties.setProperty("namespace","public");//在控制台创建好命名空间
        /**
         * 获取命名服务对象：NacosNamingService 每个命名空间对应一个，命名服务对象
         * 这个是我们的核心 spring-cloud-alibaba-starter的自动配置就是对他进行了封装同时视频spring-cloud的那些接口规范
         */
        NamingService naming = NamingFactory.createNamingService(properties);
/*      参数1：服务名称，参数2：ip，参数3：端口，参数4：集群名称(在nacos中的集群跟我们的有多个节点组成的一个个集群不同概念，nacos的集群就是一个分组)，关系一个服务名称下(例如订单服务)有多个集群，一个集群下有多个实例(例如127.0.0.1:8888)
        naming.registerInstance("nacos.test.3", "11.11.11.11", 8888, "TEST1");

        naming.registerInstance("nacos.test.3", "2.2.2.2", 9999, "DEFAULT");

        */
        /*(step1:)注册服务实例：registerInstance方法的定义有很多*/
        naming.registerInstance("nacos.test.1","127.0.0.1",8888);
        naming.registerInstance("nacos.test.1","127.0.0.1",7777);
        naming.registerInstance("nacos.test.2","DEFAULT_GROUP","127.0.0.1",9999,"DEFAULT");
        /*获取、发现服务实例 在这里nacos 已经订阅好了提供者的变更
         * (step2)*/
        System.out.println(naming.getAllInstances("nacos.test.1"));
        /*服务注销(下线)
        * (step3)*/
        naming.deregisterInstance("nacos.test.1", "127.0.0.1", 7777);
        /*下线后再次发现服务*/
        System.out.println(naming.getAllInstances("nacos.test.1"));

        Executor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("test-thread");
                        return thread;
                    }
                });
        //naming.registerInstance("nacos.test.2","DEFAULT_GROUP","127.0.0.1",6666,"DEFAULT");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("开始订阅");
        /*服务订阅   这里的服务订阅就是可以自己编写一个AbstractEventListener 的实现类然后可以监听提供者的变化然后做自己对应的业务
        * (step4)
        * */
        naming.subscribe("nacos.test.2", new AbstractEventListener() {
            //EventListener onEvent is sync to handle, If process too low in onEvent, maybe block other onEvent callback.
            //So you can override getExecutor() to async handle event.
            @Override
            public Executor getExecutor() {
                return executor;
            }
            @Override
            public void onEvent(Event event) {
                System.out.println("订阅回调通知nacos.test.2");
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
            }
        });
        //naming.registerInstance("nacos.test.1","127.0.0.1",6666);

        //naming.registerInstance("nacos.test.2","DEFAULT_GROUP","127.0.0.1",6666,"DEFAULT");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
