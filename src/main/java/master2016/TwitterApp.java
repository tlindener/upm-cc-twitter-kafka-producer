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
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import twitter4j.FilterQuery;
import twitter4j.HashtagEntity;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.auth.AccessToken;

public class TwitterApp {

	private static void loadTweetsFromFile(String filePath, String kafkaBrokerUrl) throws IOException {
		System.out.println("Load tweets from file. Broker url is: " + kafkaBrokerUrl);
		final KafkaProducer<String, String> producer = createKafkaProducer(kafkaBrokerUrl);
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			for (String line; (line = br.readLine()) != null;) {
				// JSON from String to Object
				try {
					Status status = TwitterObjectFactory.createStatus(line);
					if (status != null) {
						// send hashtag from each tweet on the kafka topic of
						// the tweets language (e.g. "es")
						for (HashtagEntity e : status.getHashtagEntities()) {
							producer.send(new ProducerRecord<String, String>(status.getLang(), e.getText()));
						}

					}
				} catch (TwitterException e1) {
					// ignore errors
				}

			}
			// line is not visible here.
		}

	}

	private static void loadTweetsFromStreamingAPI(String apiKey, String apiSecret, String tokenValue,
			String tokenSecret, String kafkaBrokerUrl) throws IOException {
		System.out.println("Load tweets from api. Broker url is: " + kafkaBrokerUrl);

		final KafkaProducer<String, String> producer = createKafkaProducer(kafkaBrokerUrl);
		// Instantiate Twitter4J API
		StatusListener listener = new StatusListener() {

			@Override
			public void onStatus(Status status) {

				// send hashtag from each tweet on the kafka topic of the tweets
				// language (e.g. "es")
				for (HashtagEntity e : status.getHashtagEntities()) {
					producer.send(new ProducerRecord<String, String>(status.getLang(), e.getText()));
				}

			}

			@Override
			public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
				// No action necessary
			}

			@Override
			public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
				// No action necessary
			}

			@Override
			public void onScrubGeo(long userId, long upToStatusId) {
				// No action necessary
			}

			@Override
			public void onStallWarning(StallWarning warning) {
				System.out.println("Got stall warning:" + warning);
			}

			@Override
			public void onException(Exception ex) {
				// appears often - requirement for exception handling undefined
			}

		};

		AccessToken accessToken = new AccessToken(tokenValue, tokenSecret);

		TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
		twitterStream.addListener(listener);
		twitterStream.setOAuthConsumer(apiKey, apiSecret);
		twitterStream.setOAuthAccessToken(accessToken);

		// set query filter to find lots of tweets (better than
		// twitterStream.sample()
		FilterQuery filter = new FilterQuery();
		String[] keywordsArray = { "trump", "clinton" };
		filter.track(keywordsArray);
		twitterStream.filter(filter);

	}

	private static KafkaProducer<String, String> createKafkaProducer(String url) throws IOException {
		// Create KafkaProducer from Zookeeper data
		System.out.println("kafkaUrl: " + url);
		final KafkaProducer<String, String> producer;
		Properties properties = new Properties();
		properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		properties.put("bootstrap.servers", url);
		producer = new KafkaProducer<>(properties);
		return producer;

	}

	public static void main(String[] args) throws IOException {
		System.out.println("Please use the kafkaUrl!");
		if (args.length < 7) {
			System.out.println("Parameters missing. Use following definition");
			System.out.println("mode apiKey apiSecret tokenValue tokenSecret zookeeperUrl twitterFilePath");
		}

		int mode = 1;
		try {
			mode = Integer.parseInt(args[0]);
		} catch (Exception ex) {

		}
		String apiKey = args[1];
		String apiSecret = args[2];
		String tokenValue = args[3];
		String tokenSecret = args[4];
		String kafkaBrokerUrl = args[5];
		String twitterFilePath = args[6];

		// depending on mode of usage use either twitter file or api
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
