package com.codeit.otboo.batch.notification.reader;

import java.util.List;
import java.util.UUID;

public record WeatherNotificationGrid(
	UUID id,
	int nx,
	int ny,
	List<String> locationNames
) {
}
