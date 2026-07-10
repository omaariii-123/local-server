
 class Main {
    public void main(String[] args){
            var test  = new ConfigLoader("config.json");
            System.out.println(test.load());
    }
}