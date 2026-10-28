package pe.edu.monitoreo;

@FunctionalInterface
public interface ISearchFuente {
    SearchService.Result search(Store.Product product,String siteUrl)throws Exception;
}
