package org.infernus.idea.checkstyle.model;

import com.intellij.openapi.project.Project;
import org.infernus.idea.checkstyle.csapi.BundledConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ConfigurationLocationTest {

    private static final String TEST_FILE = "<module name=\"Checker\">\n" +
            "<module name=\"TestFilter\">\n" +
            "  <property name=\"file\" value=\"${property-one}/a-file.xml\"/>\n" +
            "  <property name=\"url\" value=\"http://${property-two}/somewhere.xml\"/>\n" +
            "  <property name=\"something\" value=\"${property-three}\"/>\n" +
            "</module>\n" +
            "</module>";

    private static final String TEST_FILE_2 = "<module name=\"Checker\">\n" +
            "<module name=\"TestFilter\">\n" +
            "  <property name=\"file\" value=\"${property-one}/a-file.xml\"/>\n" +
            "  <property name=\"url\" value=\"http://${property-two}/somewhere.xml\"/>\n" +
            "  <property name=\"something\" value=\"${property-four}\"/>\n" +
            "</module>\n" +
            "</module>";

    private TestConfigurationLocation underTest;

    @Before
    public void setUp() {
        underTest = new TestConfigurationLocation(TEST_FILE);
        underTest.setDescription("aDescription");
    }

    @Test
    public void whenReadPropertiesAreExtracted() throws IOException {
        underTest.resolve();

        assertThat(underTest.getProperties(), hasEntry("property-one", ""));
        assertThat(underTest.getProperties(), hasEntry("property-two", ""));
        assertThat(underTest.getProperties(), hasEntry("property-three", ""));
    }

    @Test
    public void propertiesAreRereadWhenTheLocationIsChanged() throws IOException {
        underTest.resolve();

        underTest.setLocation(TEST_FILE_2);
        underTest.resolve();

        assertThat(underTest.getProperties(), hasEntry("property-one", ""));
        assertThat(underTest.getProperties(), hasEntry("property-two", ""));
        assertThat(underTest.getProperties(), hasEntry("property-four", ""));
        assertThat(underTest.getProperties(), not(hasKey("property-three")));
    }

    @Test
    public void propertyValuesAreRetainedWhenThePropertiesAreReread() throws IOException {
        underTest.resolve();

        updatePropertyOn(underTest, "property-two", "aValue");

        underTest.setLocation(TEST_FILE_2);
        underTest.resolve();

        assertThat(underTest.getProperties(), hasEntry("property-two", "aValue"));
    }

    @Test
    public void theDescriptionIsSetToThePassedStringWhenNotNull() {
        underTest.setDescription("aNewDescription");

        assertThat(underTest.getDescription(), is(equalTo("aNewDescription")));
    }

    @Test
    public void theDescriptionDefaultsToTheLocationWhenANullValueIsGiven() {
        underTest.setLocation("aLocation");
        underTest.setDescription(null);

        assertThat(underTest.getDescription(), is(equalTo("aLocation")));
    }

    @Test
    public void anUnmodifiedLocationIsNotMarkedAsChanged() throws IOException {
        final TestConfigurationLocation location1 = new TestConfigurationLocation(TEST_FILE);
        final TestConfigurationLocation location2 = new TestConfigurationLocation(TEST_FILE);

        assertThat(location1.hasChangedFrom(location2), is(false));
    }

    @Test
    public void aLocationIsChangedIfTheLocationValueHasChanged() throws IOException {
        final TestConfigurationLocation location1 = new TestConfigurationLocation(TEST_FILE);
        final TestConfigurationLocation location2 = new TestConfigurationLocation(TEST_FILE);

        location1.setLocation("aNewLocation");

        assertThat(location1.hasChangedFrom(location2), is(true));
    }

    @Test
    public void aLocationIsChangedIfTheDescriptionValueHasChanged() throws IOException {
        final TestConfigurationLocation location1 = new TestConfigurationLocation(TEST_FILE);
        final TestConfigurationLocation location2 = new TestConfigurationLocation(TEST_FILE);

        location1.setDescription("aNewDescription");

        assertThat(location1.hasChangedFrom(location2), is(true));
    }

    @Test
    public void aLocationIsChangedIfThePropertiesHaveChanged() throws IOException {
        final TestConfigurationLocation location1 = new TestConfigurationLocation(TEST_FILE);
        final TestConfigurationLocation location2 = new TestConfigurationLocation(TEST_FILE);

        updatePropertyOn(location1, "property-two", "aValue");

        assertThat(location1.hasChangedFrom(location2), is(true));
    }

    @Test
    public void aLocationsPropertiesAreIgnoredIfInTheDefaultProjectAndItCannotBeResolvedInTheDefaultProject() throws IOException {
        final DefaultProjectTestConfigurationLocation location1 = new DefaultProjectTestConfigurationLocation();
        final DefaultProjectTestConfigurationLocation location2 = new DefaultProjectTestConfigurationLocation();

        updatePropertyOn(location1, "property-two", "aValue");

        assertThat(location1.hasChangedFrom(location2), is(false));
    }

    @Test
    public void aDescriptorContainsTheLocationDescriptionAndType() {
        final ConfigurationLocation location = new TestConfigurationLocation("aLocation");

        assertThat(location.getDescriptor(), is(equalTo(format("%s:%s:%s",
                location.getType(), location.getLocation(), location.getDescription()))));
    }

    @Test
    public void equalsIgnoresProperties() throws IOException {
        final TestConfigurationLocation location1 = new TestConfigurationLocation(TEST_FILE);
        updatePropertyOn(location1, "property-one", "aValue");

        final TestConfigurationLocation location2 = new TestConfigurationLocation(TEST_FILE);
        updatePropertyOn(location2, "property-one", "anotherValue");

        assertThat(location1, is(equalTo(location2)));
    }

    @Test
    public void hashCodeIgnoresProperties() throws IOException {
        final TestConfigurationLocation location1 = new TestConfigurationLocation(TEST_FILE);
        updatePropertyOn(location1, "property-one", "aValue");

        final TestConfigurationLocation location2 = new TestConfigurationLocation(TEST_FILE);
        updatePropertyOn(location2, "property-one", "anotherValue");

        assertThat(location1.hashCode(), is(equalTo(location2.hashCode())));
    }

    @Test
    public void toStringReturnsTheDescription() {
        assertThat(underTest.toString(), is(equalTo("aDescription")));
    }

    private void updatePropertyOn(final ConfigurationLocation configurationLocation,
                                  final String propertyKey,
                                  final String propertyValue) throws IOException {
        final Map<String, String> properties = new HashMap<>(underTest.getProperties());
        properties.put(propertyKey, propertyValue);
        configurationLocation.setProperties(properties);
    }

    private class DefaultProjectTestConfigurationLocation extends ConfigurationLocation {
        public DefaultProjectTestConfigurationLocation() {
            super(ConfigurationType.LOCAL_FILE, mock(Project.class));

            when(getProject().isDefault()).thenReturn(true);
        }

        @Override
        public boolean canBeResolvedInDefaultProject() {
            return false;
        }

        @NotNull
        @Override
        protected InputStream resolveFile() throws IOException {
            throw new RuntimeException("Can't be called in default project");
        }

        @Override
        public Object clone() {
            return new DefaultProjectTestConfigurationLocation();
        }
    }

    private class TestConfigurationLocation extends ConfigurationLocation {
        public TestConfigurationLocation(final String content) {
            super(ConfigurationType.LOCAL_FILE, mock(Project.class));

            setLocation(content);
        }

        @NotNull
        @Override
        protected InputStream resolveFile() throws IOException {
            return new ByteArrayInputStream(getLocation().getBytes());
        }

        @Override
        public Object clone() {
            return new TestConfigurationLocation(getLocation());
        }
    }


    @Test
    public void testSorting() {
        final Project project = mock(Project.class);
        List<ConfigurationLocation> list = new ArrayList<>();
        FileConfigurationLocation fcl = new FileConfigurationLocation(
                mock(Project.class), ConfigurationType.LOCAL_FILE);
        fcl.setDescription("descB");
        fcl.setLocation("locB");
        list.add(fcl);
        RelativeFileConfigurationLocation rfcl1 = new RelativeFileConfigurationLocation(project);
        rfcl1.setDescription("descA");
        rfcl1.setLocation("locA");
        list.add(rfcl1);
        list.add(new BundledConfigurationLocation(BundledConfig.SUN_CHECKS, project));
        RelativeFileConfigurationLocation rfcl2 = new RelativeFileConfigurationLocation(project);
        rfcl2.setDescription("descC");
        rfcl2.setLocation("locC");
        list.add(rfcl2);
        list.add(new BundledConfigurationLocation(BundledConfig.GOOGLE_CHECKS, project));

        Collections.sort(list);

        Assert.assertEquals(BundledConfigurationLocation.class, list.get(0).getClass());
        Assert.assertTrue(list.get(0).getDescription().contains("Sun Checks"));
        Assert.assertTrue(list.contains(new BundledConfigurationLocation(BundledConfig.SUN_CHECKS, project)));
        Assert.assertEquals(BundledConfigurationLocation.class, list.get(1).getClass());
        Assert.assertTrue(list.get(1).getDescription().contains("Google Checks"));
        Assert.assertTrue(list.contains(new BundledConfigurationLocation(BundledConfig.GOOGLE_CHECKS, project)));
        Assert.assertEquals(RelativeFileConfigurationLocation.class, list.get(2).getClass());
        Assert.assertEquals("descA", list.get(2).getDescription());
        Assert.assertEquals(FileConfigurationLocation.class, list.get(3).getClass());
        Assert.assertEquals("descB", list.get(3).getDescription());
        Assert.assertEquals(RelativeFileConfigurationLocation.class, list.get(4).getClass());
        Assert.assertEquals("descC", list.get(4).getDescription());
    }
}
