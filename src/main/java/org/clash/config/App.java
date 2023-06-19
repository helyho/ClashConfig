package org.clash.config;

import org.voovan.http.client.HttpClient;
import org.voovan.http.message.Response;
import org.voovan.http.server.HttpRequest;
import org.voovan.http.server.HttpResponse;
import org.voovan.http.server.HttpRouter;
import org.voovan.http.server.WebServer;
import org.voovan.tools.TObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class App {

    public static String GROUP_PROXY_MARK = "$ProxyNames";

    public static void main(String[] args) throws Exception {

        WebServer webServer = WebServer.newInstance(args);
        webServer.get("/subconvert", new HttpRouter() {
            @Override
            public void process(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
                try {
                    List<String> proxyFilter = null;
                    String subURL = httpRequest.getParameter("sub");
                    subURL = URLDecoder.decode(subURL, "UtF-8");

                    String filter = httpRequest.getParameter("filter");
                    if (filter != null) {
                        proxyFilter = TObject.asList(filter.split(","));
                    }

                    String template = httpRequest.getParameter("template");
                    template = TObject.nullDefault(template, "default");

                    String source = convert(template, subURL, proxyFilter);
                    httpResponse.body().write(source);
                } catch (Exception e) {
                    httpResponse.body().write(e.getMessage());
                }

            }
        });
        webServer.serve();


    }


    public static String convert(String templateName, String source, List<String> proxyFilter) throws Exception {
        HttpClient httpClient = new HttpClient(source, 15);
        Response response = httpClient.send(null);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(dumperOptions);

        Map<String, Object> subscribe = yaml.loadAs(response.body().getBodyString(), Map.class);
        File templateFile = new File("./conf/template/"+templateName+".yaml");

        if(!templateFile.exists()) {
            return "templateName not exists";
        }

        Map<String, Object> templateMap = yaml.loadAs(new FileReader(templateFile), Map.class);


        List<Map<String, Object>> subProxies = TObject.cast(subscribe.get("proxies"));
        subProxies = subProxies.stream().filter(m->{
            if(proxyFilter == null) {
                return true;
            }

            String proxyName =  m.get("name").toString();
            for(String filter : proxyFilter) {
                if(proxyName.contains(filter)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());
        List<String> subProxyNames = subProxies.stream().map(m-> m.get("name").toString()).collect(Collectors.toList());

        List<Map<String, Object>> templateProxies = TObject.cast(templateMap.get("proxies"));
        templateProxies.clear();
        templateProxies.addAll(subProxies);

        List<Map<String, Object>> templateProxyGroups = TObject.cast(templateMap.get("proxy-groups"));
        for(Map<String,Object> templateProxyGroup : templateProxyGroups) {
            List<String> groupProxies = TObject.cast(templateProxyGroup.get("proxies"));
            if(groupProxies.contains(GROUP_PROXY_MARK)) {
                groupProxies.remove(GROUP_PROXY_MARK);
                groupProxies.addAll(subProxyNames);
            }
        }
        byte[] bytes = new byte[1024*1024];
        return yaml.dump(templateMap);
    }
}
