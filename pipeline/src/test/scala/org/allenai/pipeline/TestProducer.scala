package org.allenai.pipeline

import java.io.File

import org.allenai.common.testkit.UnitSpec
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import scala.util.Random

/** Created by rodneykinney on 8/19/14.
  */
class TestProducer extends UnitSpec with BeforeAndAfterAll {

  import scala.language.reflectiveCalls

  val rand = new Random

  import org.allenai.pipeline.IoHelpers._

  val outputDir = new File("test-output")

  implicit val output = new RelativeFileSystem(outputDir)

  val randomNumbers = new Producer[Iterable[Double]] with CachingDisabled {
    def create = {
      for (i <- (0 until 20)) yield rand.nextDouble
    }
  }

  val cachedRandomNumbers = new Producer[Iterable[Double]] with CachingEnabled {
    def create = {
      for (i <- (0 until 20)) yield rand.nextDouble
    }
  }

  "Uncached random numbers" should "regenerate on each invocation" in {
    randomNumbers.get should not equal (randomNumbers.get)

    val cached = randomNumbers.enableCaching

    cached.get should equal(cached.get)
  }

  "PersistedProducer" should "read from file if exists" in {
    val pStep = PersistedCollection.text("savedNumbers.txt")(randomNumbers)

    pStep.get should equal(pStep.get)

    val otherStep = PersistedCollection.text("savedNumbers.txt")(cachedRandomNumbers)
    otherStep.get should equal(pStep.get)
  }

  "CachedProducer" should "use cached value" in {
    cachedRandomNumbers.get should equal(cachedRandomNumbers.get)

    val uncached = cachedRandomNumbers.disableCaching

    uncached.get should not equal (uncached.get)
  }

  "PersistentCachedProducer" should "read from file if exists" in {
    val pStep = PersistedCollection.text("savedCachedNumbers.txt")(cachedRandomNumbers)

    pStep.get should equal(pStep.get)

    val otherStep = PersistedCollection.text("savedCachedNumbers.txt")(randomNumbers)
    otherStep.get should equal(pStep.get)
  }

  val randomIterator = new Producer[Iterator[Double]] {
    def create = {
      for (i <- (0 until 20).iterator) yield rand.nextDouble
    }
  }

  "Random iterator" should "never cache" in {
    randomIterator.get.toList should not equal (randomIterator.get.toList)
  }

  "Persisted iterator" should "re-use value" in {
    val persisted = PersistedIterator.text("randomIterator.txt")(randomIterator)
    persisted.get.toList should equal(persisted.get.toList)
  }

  "Persisted iterator" should "read from file if exists" in {
    val persisted = PersistedIterator.text("savedCachedIterator.txt")(randomIterator.enableCaching)
    val otherStep = PersistedIterator.text("savedCachedIterator.txt")(randomIterator.disableCaching)
    otherStep.get.toList should equal(persisted.get.toList)
  }

  override def beforeAll: Unit = {
    require((outputDir.exists && outputDir.isDirectory) || outputDir.mkdirs, s"Unable to create test output directory $outputDir")
  }

  override def afterAll: Unit = {
    FileUtils.deleteDirectory(outputDir)
  }
}
