# azkaban-flowlink

Azkaban的Flow间不能定义依赖关系，这样所有依赖模块必须打包在同一个Project中，
对于一些大型的拥有较多相对独立模块的Project，不利于开发中的模块切割与分工。
本项目通过简单的扩展job文件定义，调用Azkaban自身的相关代码，实现了这个功能

## 使用实例
只要简单的在Flow的job文件中定义fronting-flows项即可,现假设有4个项目:
testprj1，testprj2,testprj3,testprj4；在testprj2和testprj3的flow.job中定义

fronting-flows=testprj1:flow:sameday()

在testprj4的flow.job中定义

fronting-flows=testprj3:flow:sameday(),testprj3:flow:sameday()

这样，只要对testprj1的flow进行定时调度，testprj2:flow,testprj3:flow,testprj4:flow
就会相继运行，如果我们在调度或者手工启动一个Flow时不想其后继任务自动运行，只要简单
的在运行页面中配置Flow参数: skip-following-flow，值可以留空。

## fronting-flows格式
依赖项目间用逗号分隔，每个依赖项的格式为 projectname:flowid[:condition]

其中condition为可选，如果没有此项，将默认以12小时为条件，即所有依赖的flow
运行成功与其中最早启动的flow必须相隔12小时之内

sameday与samehour以所有flow的启动时间作为判断条件


## 编译构建
与Azkaban相同，使用gradle进行构建，迁出代码后运行

gradle build

## 打包

gradle serverDistZip

## 安装运行
安装运行与Azkaban的webserver、execserver类似，事实上，本项目的启动脚本就是用webserver
的脚本稍加修改得到的。解压安装包到Azkaban目录，与其WebServer、ExecServer同级，
在bin/start-following-server.sh中修改azkaban webserver的conf目录为实际安装路径即可
