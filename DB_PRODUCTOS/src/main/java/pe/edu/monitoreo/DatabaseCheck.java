package pe.edu.monitoreo;

public final class DatabaseCheck {
    public static void main(String[] args)throws Exception {
        var store=new Store();
        try {System.out.println(store.testConnection());if(args.length>0&&args[0].equals("--prepare")){store.initialize();System.out.println("Esquema preparado; administrador inicial requerido: "+store.needsSetup());}}
        catch(Exception e){System.err.println(DatabaseErrors.explain(e));throw new IllegalStateException("Comprobación PostgreSQL fallida; se han omitido las credenciales.");}
    }
}
