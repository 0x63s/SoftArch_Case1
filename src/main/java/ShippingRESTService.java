import org.camunda.bpm.client.ExternalTaskClient;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.Map;

public class ShippingRESTService {
	final static String SHIPPING_SERVICE_URL = "http://192.168.111.5:8080/v1/consignment/request";
	static final String EXTERNAL_TASK_TOPIC = "group10_contact_freight_forwarder";
	static final String EXTERNAL_TASK_BASIS_URL = "http://group10:Pliuzbi7vt8Ioud@192.168.111.3:8080/engine-rest"; //internal dev server, credentials are therefore visible

	public static void main(String[] args) {

		//Create a new external task client
		ExternalTaskClient client = ExternalTaskClient.create()
				.baseUrl(EXTERNAL_TASK_BASIS_URL).asyncResponseTimeout(1000).build();

		client.subscribe(EXTERNAL_TASK_TOPIC).lockDuration(1000).handler((externalTask, externalTaskService) -> {

			//Grab the variables from the process engine
			String delivery_address = (String) externalTask.getVariable("delivery_address");
			String customer_id = (String) externalTask.getVariable("customer_id");
			String contact_phone = (String) externalTask.getVariable("contact_phone");
			Long weight_temp = (Long) externalTask.getVariable("weight");
			int weight = weight_temp.intValue();

			//Print the variables
			System.out.println("Data for the consignment received from the process engine:");
			System.out.println("delivery_address: " + delivery_address);
			System.out.println("customer_id: " + customer_id);
			System.out.println("contact_phone: " + contact_phone);
			System.out.println("weight: " + weight);
			System.out.println("Sending the data to the freight forwarder...");

			//Create a REST client and send the data to the freight forwarder
			Client clientREST = ClientBuilder.newClient();
			WebTarget target = clientREST.target(SHIPPING_SERVICE_URL);

			// create the message object that we will send to the service
			NewConsignment nc = new NewConsignment();
			nc.setDestination(delivery_address);
			nc.setRecepientPhone(contact_phone);
			nc.setCustomerReference(customer_id);
			nc.setWeight(weight);

			//Set up the response objects
			Consignment response = null;
			Map<String, Object> results = new HashMap<String, Object>();

			try {
				//Send the data to the freight forwarder
				response = target.request(MediaType.APPLICATION_JSON)
						.post(Entity.entity(nc, MediaType.APPLICATION_JSON), Consignment.class);

				//Print the response
				System.out.println("\nConsignment successfully ordered!");
				System.out.println("Shipping Order ID: " + response.getOrderId());
				System.out.println("Pickup Date      : " + response.getPickupdate());
				System.out.println("Delivery Date    : " + response.getDeliverydate());

				//Send back the response to the process engine
				results.put("shipment_id", response.getOrderId());
				results.put("pick_up_date", response.getPickupdate());
				results.put("delivery_date", response.getDeliverydate());
				results.put("success", true);

			} catch (WebApplicationException e) {
				//In case of an error...
				System.out.println("Error while ordering consignment:");
				if (e.getResponse().getStatus() == 501) {
					System.out.println("Request was not possible, please use hotline to order");
				} else {
					System.err.println(e.getMessage());
				}

				//Send back success = false
				results.put("success", false);
			}

			//Close the REST client and complete the external task
			clientREST.close();
			externalTaskService.complete(externalTask, results);
		}).open();

	}

}
