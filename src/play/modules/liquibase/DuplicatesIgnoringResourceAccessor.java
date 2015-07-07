package play.modules.liquibase;

import liquibase.resource.ClassLoaderResourceAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class DuplicatesIgnoringResourceAccessor extends ClassLoaderResourceAccessor {
  public DuplicatesIgnoringResourceAccessor(ClassLoader classloader) {
    super(classloader);
  }

  @Override public Set<InputStream> getResourcesAsStream(String path) throws IOException {
    Set<InputStream> resources = super.getResourcesAsStream(path);
    return resources == null || resources.size() <= 1 ? resources : firstElement(resources);
  }

  public HashSet<InputStream> firstElement(Set<InputStream> resources) {
    HashSet<InputStream> result = new HashSet<>();
    result.add(resources.iterator().next());
    return result;
  }
}
