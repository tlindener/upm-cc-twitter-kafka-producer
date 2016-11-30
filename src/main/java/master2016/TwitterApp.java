/**
* The twitter-hastag-topology is an implementation of Apache Storm to analyze tweets coming from Kafka
* @author  Tobias Lindener
* @version 1.0
* @since   2016-11-22 
*/
package master2016;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.protocol.SecurityProtocol;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kafka.cluster.Broker;
import twitter4j.FilterQuery;
import twitter4j.HashtagEntity;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.auth.AccessToken;

public class TwitterApp {

	private static void loadTweetsFromFile(String filePath, String kafkaBrokerUrl) throws IOException {
		final KafkaProducer<String, String> producer = createKafkaProducer(kafkaBrokerUrl);
		ObjectMapper mapper = new ObjectMapper();
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			for (String line; (line = br.readLine()) != null;) {
				// JSON from String to Object
				try {
					Tweet status = mapper.readValue(line, Tweet.class);
					if (status != null) {
						if (status.getEntities() != null) {
							for (Hashtag e : status.getEntities().getHashtags()) {
								producer.send(new ProducerRecord<String, String>("hashtags-" + status.getLang(),
										e.getText()));
							}
						}
					}
				} catch (JsonParseException ex) {

				}

			}
			// line is not visible here.
		}

	}

	private static void loadTweetsFromStreamingAPI(String apiKey, String apiSecret, String tokenValue,
			String tokenSecret, String kafkaBrokerUrl) throws IOException {

		final KafkaProducer<String, String> producer = createKafkaProducer(kafkaBrokerUrl);

		StatusListener listener = new StatusListener() {

			@Override
			public void onStatus(Status status) {

				// send lots of messages
				for (HashtagEntity e : status.getHashtagEntities()) {
					producer.send(new ProducerRecord<String, String>("hashtags-" + status.getLang(), e.getText()));
				}

			}

			@Override
			public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
				// System.out.println("Got a status deletion notice id:" +
				// statusDeletionNotice.getStatusId());
			}

			@Override
			public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
				// System.out.println("Got track limitation notice:" +
				// numberOfLimitedStatuses);
			}

			@Override
			public void onScrubGeo(long userId, long upToStatusId) {
				// System.out.println("Got scrub_geo event userId:" + userId + "
				// upToStatusId:" + upToStatusId);
			}

			@Override
			public void onStallWarning(StallWarning warning) {
				System.out.println("Got stall warning:" + warning);
			}

			@Override
			public void onException(Exception ex) {
				ex.printStackTrace();
				// producer.close();
			}

		};

		AccessToken accessToken = new AccessToken(tokenValue, tokenSecret);

		TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
		twitterStream.addListener(listener);
		twitterStream.setOAuthConsumer(apiKey, apiSecret);
		twitterStream.setOAuthAccessToken(accessToken);
		FilterQuery filtre = new FilterQuery();
		String[] keywordsArray = { "trump", "clinton" };
		filtre.track(keywordsArray);
		twitterStream.filter(filtre);

	}

	private static KafkaProducer<String, String> createKafkaProducer(String url) throws IOException {
		final KafkaProducer<String, String> producer;
		ZooKeeper zk = new ZooKeeper(url, 10000, null);
		List<String> brokerList = new ArrayList<String>();
		try {
			List<String> ids;

			ids = zk.getChildren("/brokers/ids", false);
			System.out.println("ids:" + ids);
			for (String id : ids) {
				String brokerInfoString = new String(zk.getData("/brokers/ids/" + id, false, null));
				Broker broker = Broker.createBroker(Integer.valueOf(id), brokerInfoString);
				if (broker != null) {
					System.out.println(broker.getBrokerEndPoint(SecurityProtocol.PLAINTEXT).connectionString());
					brokerList.add(broker.getBrokerEndPoint(SecurityProtocol.PLAINTEXT).connectionString());

				}
			}
		} catch (KeeperException | InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		Properties properties = new Properties();
		properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		properties.put("bootstrap.servers", String.join(",", brokerList));
		producer = new KafkaProducer<>(properties);
		return producer;

	}

	public static void main(String[] args) throws IOException {
		int mode = 1;
		Integer.parseInt(args[0]);
		String apiKey = args[1];
		String apiSecret = args[2];
		String tokenValue = args[3];
		String tokenSecret = args[4];
		String kafkaBrokerUrl = args[5];
		String twitterFilePath = args[6];

		switch (mode) {
		case 1:
			loadTweetsFromFile(twitterFilePath, kafkaBrokerUrl);
			break;
		case 2:
			loadTweetsFromStreamingAPI(apiKey, apiSecret, tokenValue, tokenSecret, kafkaBrokerUrl);
			break;
		default:
			break;
		}

	}
}
