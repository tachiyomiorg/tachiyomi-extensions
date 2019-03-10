package android.net;

public class Uri {

    public static class Builder{
        public Builder appendQueryParameter(String key, String value){
            return new Builder();
        }
    }
    public static Uri parse(String str){
            return new Uri();
    }
    public  Builder buildUpon(){
        return new Builder();
    }

}
