/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.adaptive.media.image.internal.handler;

import com.liferay.adaptive.media.AMAttribute;
import com.liferay.adaptive.media.AdaptiveMedia;
import com.liferay.adaptive.media.exception.AMException;
import com.liferay.adaptive.media.exception.AMRuntimeException;
import com.liferay.adaptive.media.finder.AMQuery;
import com.liferay.adaptive.media.image.configuration.AMImageConfigurationEntry;
import com.liferay.adaptive.media.image.configuration.AMImageConfigurationHelper;
import com.liferay.adaptive.media.image.finder.AMImageFinder;
import com.liferay.adaptive.media.image.finder.AMImageQueryBuilder;
import com.liferay.adaptive.media.image.internal.configuration.AMImageAttributeMapping;
import com.liferay.adaptive.media.image.internal.configuration.AMImageConfigurationEntryImpl;
import com.liferay.adaptive.media.image.internal.finder.AMImageQueryBuilderImpl;
import com.liferay.adaptive.media.image.internal.processor.AMImage;
import com.liferay.adaptive.media.image.internal.util.Tuple;
import com.liferay.adaptive.media.image.processor.AMImageAttribute;
import com.liferay.adaptive.media.image.processor.AMImageProcessor;
import com.liferay.adaptive.media.processor.AMAsyncProcessor;
import com.liferay.adaptive.media.processor.AMAsyncProcessorLocator;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.util.GetterUtil;

import java.io.InputStream;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Adolfo Pérez
 * @author Alejandro Tardín
 */
public class AMImageRequestHandlerTest {

	@Before
	public void setUp() throws PortalException {
		_fileVersion = _getFileVersion();

		Mockito.doReturn(
			_amAsyncProcessor
		).when(
			_amAsyncProcessorLocator
		).locateForClass(
			FileVersion.class
		);

		_amImageRequestHandler.setAMAsyncProcessorLocator(
			_amAsyncProcessorLocator);
		_amImageRequestHandler.setAMImageFinder(_amImageFinder);
		_amImageRequestHandler.setPathInterpreter(_pathInterpreter);
		_amImageRequestHandler.setAMImageConfigurationHelper(
			_amImageConfigurationHelper);
	}

	@Test(expected = AMRuntimeException.class)
	public void testFinderFailsWithMediaProcessorException() throws Exception {
		AMImageConfigurationEntry amImageConfigurationEntry =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 200, 500);

		HttpServletRequest request = _createRequestFor(
			_fileVersion, amImageConfigurationEntry);

		Mockito.when(
			_amImageFinder.getAdaptiveMediaStream(Mockito.any(Function.class))
		).thenThrow(
			AMException.class
		);

		_amImageRequestHandler.handleRequest(request);
	}

	@Test(expected = AMRuntimeException.class)
	public void testFinderFailsWithPortalException() throws Exception {
		AMImageConfigurationEntry getConfigurationEntryFilter =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 200, 500);

		HttpServletRequest request = _createRequestFor(
			_fileVersion, getConfigurationEntryFilter);

		Mockito.when(
			_amImageFinder.getAdaptiveMediaStream(Mockito.any(Function.class))
		).thenThrow(
			PortalException.class
		);

		_amImageRequestHandler.handleRequest(request);
	}

	@Test
	public void testInvalidPath() throws Exception {
		Mockito.when(
			_pathInterpreter.interpretPath(Mockito.anyString())
		).thenReturn(
			Optional.empty()
		);

		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

		Optional<AdaptiveMedia<AMImageProcessor>> adaptiveMediaOptional =
			_amImageRequestHandler.handleRequest(request);

		Assert.assertFalse(adaptiveMediaOptional.isPresent());
	}

	@Test(expected = NullPointerException.class)
	public void testNullRequest() throws Exception {
		_amImageRequestHandler.handleRequest(null);
	}

	@Test
	public void testPathInterpreterFailure() throws Exception {
		Mockito.when(
			_pathInterpreter.interpretPath(Mockito.anyString())
		).thenThrow(
			AMRuntimeException.class
		);

		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

		Optional<AdaptiveMedia<AMImageProcessor>> adaptiveMediaOptional =
			_amImageRequestHandler.handleRequest(request);

		Assert.assertFalse(adaptiveMediaOptional.isPresent());
	}

	@Test
	public void testReturnsTheClosestMatchByWidthIfNoExactMatchPresentAndRunsTheProcess()
		throws Exception {

		AMImageConfigurationEntry getConfigurationEntryFilter =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 200, 500);

		AMImageConfigurationEntry closestAMImageConfigurationEntry =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 201, 501);

		AMImageConfigurationEntry fartherAMImageConfigurationEntry =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 301, 501);

		AMImageConfigurationEntry farthestAMImageConfigurationEntry =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 401, 501);

		AdaptiveMedia<AMImageProcessor> closestAdaptiveMedia =
			_createAdaptiveMedia(
				_fileVersion, closestAMImageConfigurationEntry);

		AdaptiveMedia<AMImageProcessor> fartherAdaptiveMedia =
			_createAdaptiveMedia(
				_fileVersion, fartherAMImageConfigurationEntry);

		AdaptiveMedia<AMImageProcessor> farthestAdaptiveMedia =
			_createAdaptiveMedia(
				_fileVersion, farthestAMImageConfigurationEntry);

		_mockClosestMatch(
			_fileVersion, getConfigurationEntryFilter,
			Arrays.asList(
				farthestAdaptiveMedia, closestAdaptiveMedia,
				fartherAdaptiveMedia));

		HttpServletRequest request = _createRequestFor(
			_fileVersion, getConfigurationEntryFilter);

		Assert.assertEquals(
			Optional.of(closestAdaptiveMedia),
			_amImageRequestHandler.handleRequest(request));

		Mockito.verify(
			_amAsyncProcessor
		).triggerProcess(
			_fileVersion, String.valueOf(_fileVersion.getFileVersionId())
		);
	}

	@Test
	public void testReturnsTheExactMatchIfPresentAndDoesNotRunTheProcess()
		throws Exception {

		AMImageConfigurationEntry amImageConfigurationEntry =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 200, 500);

		AdaptiveMedia<AMImageProcessor> adaptiveMedia = _createAdaptiveMedia(
			_fileVersion, amImageConfigurationEntry);

		_mockExactMatch(_fileVersion, amImageConfigurationEntry, adaptiveMedia);

		HttpServletRequest request = _createRequestFor(
			_fileVersion, amImageConfigurationEntry);

		Assert.assertEquals(
			Optional.of(adaptiveMedia),
			_amImageRequestHandler.handleRequest(request));

		Mockito.verify(
			_amAsyncProcessor, Mockito.never()
		).triggerProcess(
			_fileVersion, String.valueOf(_fileVersion.getFileVersionId())
		);
	}

	@Test
	public void testReturnsTheRealImageIfThereAreNoAdaptiveMediasAndRunsTheProcess()
		throws Exception {

		AMImageConfigurationEntry amImageConfigurationEntry =
			_createAdaptiveMediaImageConfigurationEntry(
				_fileVersion.getCompanyId(), 200, 500);

		HttpServletRequest request = _createRequestFor(
			_fileVersion, amImageConfigurationEntry);

		Mockito.when(
			_amImageFinder.getAdaptiveMediaStream(Mockito.any(Function.class))
		).thenAnswer(
			invocation -> Stream.empty()
		);

		Optional<AdaptiveMedia<AMImageProcessor>> adaptiveMediaOptional =
			_amImageRequestHandler.handleRequest(request);

		Assert.assertTrue(adaptiveMediaOptional.isPresent());

		AdaptiveMedia<AMImageProcessor> adaptiveMedia =
			adaptiveMediaOptional.get();

		Assert.assertEquals(
			_fileVersion.getContentStream(false),
			adaptiveMedia.getInputStream());

		Assert.assertEquals(
			Optional.of(_fileVersion.getFileName()),
			adaptiveMedia.getValueOptional(
				AMAttribute.getFileNameAMAttribute()));

		Assert.assertEquals(
			Optional.of(_fileVersion.getMimeType()),
			adaptiveMedia.getValueOptional(
				AMAttribute.getContentTypeAMAttribute()));

		Optional<Integer> contentLength = adaptiveMedia.getValueOptional(
			AMAttribute.getContentLengthAMAttribute());

		Assert.assertEquals(_fileVersion.getSize(), (long)contentLength.get());

		Mockito.verify(
			_amAsyncProcessor
		).triggerProcess(
			_fileVersion, String.valueOf(_fileVersion.getFileVersionId())
		);
	}

	private AdaptiveMedia<AMImageProcessor> _createAdaptiveMedia(
			FileVersion fileVersion,
			AMImageConfigurationEntry amImageConfigurationEntry)
		throws Exception {

		Map<String, String> properties = new HashMap<>();

		AMAttribute<Object, String> configurationUuidAMAttribute =
			AMAttribute.getConfigurationUuidAMAttribute();

		properties.put(
			configurationUuidAMAttribute.getName(),
			amImageConfigurationEntry.getUUID());

		AMAttribute<Object, String> fileNameAMAttribute =
			AMAttribute.getFileNameAMAttribute();

		properties.put(
			fileNameAMAttribute.getName(), fileVersion.getFileName());

		AMAttribute<Object, String> contentTypeAMAttribute =
			AMAttribute.getContentTypeAMAttribute();

		properties.put(
			contentTypeAMAttribute.getName(), fileVersion.getMimeType());

		AMAttribute<Object, Integer> contentLengthAMAttribute =
			AMAttribute.getContentLengthAMAttribute();

		properties.put(
			contentLengthAMAttribute.getName(),
			String.valueOf(fileVersion.getSize()));

		Map<String, String> configurationEntryProperties =
			amImageConfigurationEntry.getProperties();

		properties.put(
			AMImageAttribute.IMAGE_WIDTH.getName(),
			configurationEntryProperties.get("max-width"));
		properties.put(
			AMImageAttribute.IMAGE_HEIGHT.getName(),
			configurationEntryProperties.get("max-height"));

		return new AMImage(
			() -> {
				try {
					return fileVersion.getContentStream(false);
				}
				catch (PortalException pe) {
					throw new AMRuntimeException(pe);
				}
			},
			AMImageAttributeMapping.fromProperties(properties), null);
	}

	private AMImageConfigurationEntry
		_createAdaptiveMediaImageConfigurationEntry(
			long companyId, int width, int height) {

		String uuid = "testUuid" + Math.random();

		final Map<String, String> properties = new HashMap<>();

		properties.put("configuration-uuid", uuid);
		properties.put("max-height", String.valueOf(height));
		properties.put("max-width", String.valueOf(width));

		AMImageConfigurationEntryImpl amImageConfigurationEntry =
			new AMImageConfigurationEntryImpl(uuid, uuid, properties);

		Mockito.when(
			_amImageConfigurationHelper.getAMImageConfigurationEntry(
				companyId, amImageConfigurationEntry.getUUID())
		).thenReturn(
			Optional.of(amImageConfigurationEntry)
		);

		return amImageConfigurationEntry;
	}

	private HttpServletRequest _createRequestFor(
		FileVersion fileVersion,
		AMImageConfigurationEntry amImageConfigurationEntry) {

		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

		Mockito.when(
			request.getPathInfo()
		).thenReturn(
			"pathInfo"
		);

		Map<String, String> pathProperties = new HashMap<>();

		pathProperties.put(
			"configuration-uuid", amImageConfigurationEntry.getUUID());

		Mockito.when(
			_pathInterpreter.interpretPath(request.getPathInfo())
		).thenReturn(
			Optional.of(Tuple.of(fileVersion, pathProperties))
		);

		return request;
	}

	private FileVersion _getFileVersion() throws PortalException {
		FileVersion fileVersion = Mockito.mock(FileVersion.class);

		Mockito.when(
			fileVersion.getCompanyId()
		).thenReturn(
			1234L
		);

		Mockito.when(
			fileVersion.getContentStream(false)
		).thenReturn(
			Mockito.mock(InputStream.class)
		);

		Mockito.when(
			fileVersion.getMimeType()
		).thenReturn(
			"image/jpg"
		);

		Mockito.when(
			fileVersion.getFileName()
		).thenReturn(
			"fileName"
		);

		Mockito.when(
			fileVersion.getSize()
		).thenReturn(
			2048L
		);

		return fileVersion;
	}

	private void _mockClosestMatch(
			FileVersion fileVersion,
			AMImageConfigurationEntry amImageConfigurationEntry,
			List<AdaptiveMedia<AMImageProcessor>> adaptiveMediaList)
		throws Exception {

		Mockito.when(
			_amImageFinder.getAdaptiveMediaStream(Mockito.any(Function.class))
		).thenAnswer(
			invocation -> {
				Function<AMImageQueryBuilder, AMQuery>
					amImageQueryBuilderFunction = invocation.getArgumentAt(
						0, Function.class);

				AMImageQueryBuilderImpl amImageQueryBuilderImpl =
					new AMImageQueryBuilderImpl();

				AMQuery amQuery = amImageQueryBuilderFunction.apply(
					amImageQueryBuilderImpl);

				Map<AMAttribute<AMImageProcessor, ?>,
					Object> amAttributes =
						amImageQueryBuilderImpl.getAMAttributes();

				Object queryBuilderWidth = amAttributes.get(
					AMImageAttribute.IMAGE_WIDTH);

				Object queryBuilderHeight = amAttributes.get(
					AMImageAttribute.IMAGE_HEIGHT);

				Map<String, String> properties =
					amImageConfigurationEntry.getProperties();

				int configurationWidth = GetterUtil.getInteger(
					properties.get("max-width"));

				int configurationHeight = GetterUtil.getInteger(
					properties.get("max-height"));

				if (AMImageQueryBuilderImpl.AM_QUERY.equals(amQuery) &&
					fileVersion.equals(
						amImageQueryBuilderImpl.getFileVersion()) &&
					(amImageQueryBuilderImpl.getConfigurationUuid() == null) &&
					queryBuilderWidth.equals(configurationWidth) &&
					queryBuilderHeight.equals(configurationHeight)) {

					return adaptiveMediaList.stream();
				}

				return Stream.empty();
			}
		);
	}

	private void _mockExactMatch(
			FileVersion fileVersion,
			AMImageConfigurationEntry amImageConfigurationEntry,
			AdaptiveMedia<AMImageProcessor> adaptiveMedia)
		throws Exception {

		Mockito.when(
			_amImageFinder.getAdaptiveMediaStream(Mockito.any(Function.class))
		).thenAnswer(
			invocation -> {
				Function<AMImageQueryBuilder, AMQuery>
					amImageQueryBuilderFunction = invocation.getArgumentAt(
						0, Function.class);

				AMImageQueryBuilderImpl amImageQueryBuilderImpl =
					new AMImageQueryBuilderImpl();

				AMQuery amQuery = amImageQueryBuilderFunction.apply(
					amImageQueryBuilderImpl);

				if (!AMImageQueryBuilderImpl.AM_QUERY.equals(amQuery)) {
					return Stream.empty();
				}

				if (fileVersion.equals(
						amImageQueryBuilderImpl.getFileVersion())) {

					Predicate<AMImageConfigurationEntry>
						amImageConfigurationEntryFilter =
							amImageQueryBuilderImpl.
								getConfigurationEntryFilter();

					if (amImageConfigurationEntryFilter.test(
							amImageConfigurationEntry)) {

						return Stream.of(adaptiveMedia);
					}
				}

				return Stream.empty();
			}
		);
	}

	private final AMAsyncProcessor<FileVersion, ?> _amAsyncProcessor =
		Mockito.mock(AMAsyncProcessor.class);
	private final AMAsyncProcessorLocator _amAsyncProcessorLocator =
		Mockito.mock(AMAsyncProcessorLocator.class);
	private final AMImageConfigurationHelper _amImageConfigurationHelper =
		Mockito.mock(AMImageConfigurationHelper.class);
	private final AMImageFinder _amImageFinder = Mockito.mock(
		AMImageFinder.class);
	private final AMImageRequestHandler _amImageRequestHandler =
		new AMImageRequestHandler();
	private FileVersion _fileVersion;
	private final PathInterpreter _pathInterpreter = Mockito.mock(
		PathInterpreter.class);

}