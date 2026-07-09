 package src;

 class Main {
    public void main(String[] args){
            var test  = new ConfigLoader("config.json");
            var configs = test.load();
            if (configs != null){
                for (var config : configs){
                    System.out.println("Server Config:");
                    System.out.println("Host: " + config.host);
                    System.out.println("Ports: " + config.ports);
                    System.out.println("Client Max Body Size: " + config.clientMaxBodySize);
                    System.out.println("Is Default Server: " + config.isDefaultServer);
                    System.out.println("Error Pages: " + config.errorPages);
                    System.out.println("Routes:");
                    for (var route : config.routes){
                        System.out.println("  Path: " + route.path);
                        System.out.println("  Root: " + route.root);
                        System.out.println("  Accepted Methods: " + route.acceptedMethods);
                        System.out.println("  Autoindex: " + route.autoindex);
                        if (route.redirection != null){
                            System.out.println("  Redirection: " + route.redirection);
                        }
                        if (route.cgi != null){
                            System.out.println("  CGI: " + route.cgi);
                        }
                    }
                }
                ConfigLoader.runGauntletTests(configs);
            }
        }
}