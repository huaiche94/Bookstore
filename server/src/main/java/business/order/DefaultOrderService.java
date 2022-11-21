package business.order;

import api.ApiException;
import business.BookstoreDbException;
import business.JdbcUtils;
import business.book.Book;
import business.book.BookDao;
import business.book.BookForm;
import business.cart.ShoppingCart;
import business.cart.ShoppingCartItem;
import business.customer.Customer;
import business.customer.CustomerDao;
import business.customer.CustomerForm;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Date;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DefaultOrderService implements OrderService {

	private BookDao bookDao;
	private CustomerDao customerDao;
	private OrderDao orderDao;
	private LineItemDao lineItemDao;
	private Random random = new Random();

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}
	public void setCustomerDao(CustomerDao customerDao) {this.customerDao = customerDao;}
	public void setOrderDao(OrderDao orderDao) {this.orderDao = orderDao;}
	public void setLineItemDao(LineItemDao lineItemDao) {this.lineItemDao = lineItemDao;}

	@Override
	public OrderDetails getOrderDetails(long orderId) {
		Order order = orderDao.findByOrderId(orderId);
		Customer customer = customerDao.findByCustomerId(order.getCustomerId());
		List<LineItem> lineItems = lineItemDao.findByOrderId(orderId);
		List<Book> books = lineItems
				.stream()
				.map(lineItem -> bookDao.findByBookId(lineItem.getBookId()))
				.collect(Collectors.toList());
		return new OrderDetails(order, customer, lineItems, books);
	}

	@Override
    public long placeOrder(CustomerForm customerForm, ShoppingCart cart) {

		validateCustomer(customerForm);
		validateCart(cart);

		try (Connection connection = JdbcUtils.getConnection()) {
			Date date = getDate(
					customerForm.getCcExpiryMonth(),
					customerForm.getCcExpiryYear());
			return performPlaceOrderTransaction(
					customerForm.getName(),
					customerForm.getAddress(),
					customerForm.getPhone(),
					customerForm.getEmail(),
					customerForm.getCcNumber(),
					date, cart, connection);
		} catch (SQLException e) {
			throw new BookstoreDbException("Error during close connection for customer order", e);
		}
	}
	private Date getDate(String monthString, String yearString) {
		int month = Integer.parseInt(monthString);
		int year = Integer.parseInt(yearString);
		return Date.from(YearMonth.of(year, month).atEndOfMonth().atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private long performPlaceOrderTransaction(
			String name, String address, String phone,
			String email, String ccNumber, Date date,
			ShoppingCart cart, Connection connection) {
		try {
			//disable the auto-commit mode
			connection.setAutoCommit(false);
			long customerId = customerDao.create(
					connection, name, address, phone, email,
					ccNumber, date);
			long customerOrderId = orderDao.create(
					connection,
					cart.getComputedSubtotal() + cart.getSurcharge(),
					generateConfirmationNumber(), customerId);
			for (ShoppingCartItem item : cart.getItems()) {
				lineItemDao.create(connection, customerOrderId,
						item.getBookId(), item.getQuantity());
			}
			connection.commit();
			return customerOrderId;
		} catch (Exception e) {
			try {
				//aborting the transaction, clear the reserved space
				connection.rollback();
			} catch (SQLException e1) {
				throw new BookstoreDbException("Failed to roll back transaction", e1);
			}
			return 0;
		}
	}

	private int generateConfirmationNumber() {
		return random.nextInt(999999999);
	}

	private void validateCustomer(CustomerForm customerForm) {

		if (!nameIsValid(customerForm.getName())) {
			throw new ApiException.InvalidParameter("Invalid name field");
		}
		if(!addressIsValid(customerForm.getAddress())) {
			throw new ApiException.InvalidParameter("Invalid address field");
		}
		if(!phoneIsValid(customerForm.getPhone())) {
			throw new ApiException.InvalidParameter("Invalid phone field");
		}
		if(!emailIsValid(customerForm.getEmail())) {
			throw new ApiException.InvalidParameter("Invalid email field");
		}
		if(!ccNumberIsValid(customerForm.getCcNumber())) {
			throw new ApiException.InvalidParameter("Invalid credit card number field");
		}
		if (!expiryDateIsValid(customerForm.getCcExpiryMonth(), customerForm.getCcExpiryYear())) {
			throw new ApiException.InvalidParameter("Invalid expiry date");
		}
	}

	private boolean nameIsValid(String name) {
		if(name == null) return false;
		if(name.equals("")) return false;
		if(name.length() < 4 || name.length() > 45) return false;
		return true;
	}
	private boolean addressIsValid(String address) {
		if(address == null || address.equals("")) return false;
		if(address.length() < 4 || address.length() > 45) return false;
		return true;
	}
	private boolean phoneIsValid(String phone) {
		if(phone == null) return false;
		if(phone.equals("")) return false;
		//replace parens
		phone = phone.replaceAll("\\D", "");
		//should have a number (no letters) with exactly 10 digits
		if(!phone.matches("[\\d]+")) return false;
		if(phone.length() != 10) return false;

		return true;
	}
	private static Pattern SIMPLE_EMAIL_REGEX = Pattern.compile("^\\S+@\\S+$");
	private boolean doesNotLookLikeAnEmail(String email) {
		return !SIMPLE_EMAIL_REGEX.matcher(email).matches();
	}
	private boolean emailIsValid(String email) {
		if(email == null || email.length() == 0 || doesNotLookLikeAnEmail(email) || email.endsWith("."))
			return false;
		return true;
	}
	private boolean ccNumberIsValid (String ccNumber) {
		if(ccNumber == null || ccNumber.equals("")) return false;
		String digits = ccNumber.replaceAll("\\D", "");
		if(digits.length() < 14 || digits.length() > 16) return false;
		return true;
	}
	private boolean expiryDateIsValid(String ccExpiryMonth, String ccExpiryYear) {

		// TODO: return true when the provided month/year is after the current month/yeaR
		// HINT: Use Integer.parseInt and the YearMonth class
		if (ccExpiryMonth == null || ccExpiryYear == null) return false;
		if(ccExpiryMonth.equals("") || ccExpiryYear.equals("")) return false;

		int inputMonth, inputYear;
		try {
			inputMonth = Integer.parseInt(ccExpiryMonth);
			inputYear = Integer.parseInt(ccExpiryYear);
		} catch (NumberFormatException exception) {
			return false;
		}
		if(inputMonth < 1 || inputMonth > 12) return false;
		if(inputYear < 1) return false;
		LocalDate today = LocalDate.now();
		if(inputYear < today.getYear()) return false;
		if(inputYear == today.getYear() && inputMonth < today.getMonthValue()) return false;
		return true;
	}

	private void validateCart(ShoppingCart cart) {

		if (cart.getItems().size() <= 0) {
			throw new ApiException.InvalidParameter("Cart is empty.");
		}

		cart.getItems().forEach(item-> {
			if (item.getQuantity() <= 0 || item.getQuantity() > 99) {
				throw new ApiException.InvalidParameter("Invalid quantity");
			}
			Book databaseBook = bookDao.findByBookId(item.getBookId());
			BookForm bookForm = item.getBookForm();
			if(bookForm.getPrice() != databaseBook.getPrice()) {
				throw new ApiException.InvalidParameter("Invalid price");
			}
			if(bookForm.getCategoryId() != databaseBook.getCategoryId()) {
				throw new ApiException.InvalidParameter("Invalid category");
			}
		});
	}

}
