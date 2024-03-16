import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.client.ExternalTaskClient;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

public class ShippingRESTService {
	final static String SERVICE_URL = "http://192.168.111.5:8080/v1/consignment/request";

	public static void main(String[] args) {
		// Verbindung zur Workflow Engine aufbauen
		ExternalTaskClient client = ExternalTaskClient.create()
				.baseUrl("http://group10:Pliuzbi7vt8Ioud@192.168.111.3:8080/engine-rest").asyncResponseTimeout(1000).build();
		
		// für das Topic "group123_sendGreeting" registrieren und die folgende Funktion bei jedem Aufruf ausführen
		client.subscribe("group10_contact_freight_forwarder").lockDuration(1000).handler((externalTask, externalTaskService) -> {

			// Variable "name" aus der Prozessinstanz auslesen
			String delivery_address = (String) externalTask.getVariable("delivery_address");
			String customer_id = (String) externalTask.getVariable("customer_id");
			String contact_phone = (String) externalTask.getVariable("contact_phone");
			Long weight_temp = (Long) externalTask.getVariable("weight");
			//convert Long to int
			int weight = weight_temp.intValue();

			//Print the variables
			System.out.println("Data for the consignment received from the process engine:");
			System.out.println("delivery_address: " + delivery_address);
			System.out.println("customer_id: " + customer_id);
			System.out.println("contact_phone: " + contact_phone);
			System.out.println("weight: " + weight);
			System.out.println("Sending the data to the freight forwarder...");

			// Create a REST Service Client and a Target where the client should send
			// requests to
			Client clientREST = ClientBuilder.newClient();
			WebTarget target = clientREST.target(SERVICE_URL);

			// create the message object that we will send to the service
			NewConsignment nc = new NewConsignment();
			nc.setDestination(delivery_address);
			nc.setRecepientPhone(contact_phone);
			nc.setCustomerReference(customer_id);
			nc.setWeight(weight);

			Consignment response = null;
			Map<String, Object> results = new HashMap<String, Object>();

			try {
				response = target.request(MediaType.APPLICATION_JSON)
						.post(Entity.entity(nc, MediaType.APPLICATION_JSON), Consignment.class);

				System.out.println("\nConsignment successfully ordered!");
				System.out.println("Shipping Order ID: " + response.getOrderId());
				System.out.println("Pickup Date      : " + response.getPickupdate());
				System.out.println("Delivery Date    : " + response.getDeliverydate());

				results.put("shipment_id", response.getOrderId());
				results.put("pick_up_date", response.getPickupdate());
				results.put("delivery_date", response.getDeliverydate());
				results.put("success", true);

			} catch (WebApplicationException e) {
				System.out.println("Error while ordering consignment:");
				if (e.getResponse().getStatus() == 501) {
					System.out.println("Request was not possible, please use hotline to order");
				} else {
					System.err.println(e.getMessage());
				}

				results.put("success", false);
			}

			clientREST.close();

			externalTaskService.complete(externalTask, results);
		}).open();

	}

}
