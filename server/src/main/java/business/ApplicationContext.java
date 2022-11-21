
package business;

import business.book.BookDao;
import business.book.BookDaoJdbc;
import business.category.CategoryDao;
import business.category.CategoryDaoJdbc;
import business.customer.CustomerDao;
import business.customer.CustomerDaoJdbc;
import business.order.*;


/**
 * the single obj that hold all the DAO classes
 */
    public class ApplicationContext {

    private final BookDao bookDao;
    private final CategoryDao categoryDao;
    private final OrderService orderService;
    private final OrderDao orderDao;
    private final LineItemDao lineItemDao;
    private final CustomerDao customerDao;
    public static ApplicationContext INSTANCE = new ApplicationContext();

    private ApplicationContext() {
        bookDao = new BookDaoJdbc();
        categoryDao = new CategoryDaoJdbc();
        orderService = new DefaultOrderService();
        orderDao = new OrderDaoJdbc();
        lineItemDao = new LineItemDaoJdbc();
        customerDao = new CustomerDaoJdbc();
        ((DefaultOrderService)orderService).setBookDao(bookDao);
        ((DefaultOrderService)orderService).setOrderDao(orderDao);
        ((DefaultOrderService)orderService).setLineItemDao(lineItemDao);
        ((DefaultOrderService)orderService).setCustomerDao(customerDao);
    }

    public BookDao getBookDao() { return bookDao; }
    public CategoryDao getCategoryDao() {
        return categoryDao;
    }
    public OrderService getOrderService() {
        return orderService;
    }
    public OrderDao getOrderDao() {return orderDao;}
    public LineItemDao getLineItemDao() {return lineItemDao;}
    public CustomerDao getCustomerDao() {return customerDao;}
}
