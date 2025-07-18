package org.example.calculation

import kotlinx.serialization.json.Json
import org.example.calculation.models.MaterialPriceList
import org.example.calculation.models.OperationPriceList
import org.example.calculation.models.ProductPriceList
import org.slf4j.LoggerFactory

class PriceListProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    val products: ProductPriceList
    val materials: MaterialPriceList
    val operations: OperationPriceList

    init {
        logger.info("Загрузка прайс-листов...")
        products = loadPriceList("pricelist/products.json")
        materials = loadPriceList("pricelist/materials.json")
        operations = loadPriceList("pricelist/operations.json")
        logger.info("Прайс-листы успешно загружены.")
    }

    /**
     * Обобщенная (generic) функция для загрузки и парсинга любого JSON-файла
     * из ресурсов в соответствующий data-класс.
     * @param resourcePath Путь к файлу в папке resources.
     * @return Объект типа T (например, ProductPriceList).
     */
    private inline fun <reified T> loadPriceList(resourcePath: String): T {
        logger.debug("Загрузка ресурса: {}", resourcePath)
        val jsonContent = try {
            this::class.java.classLoader.getResource(resourcePath)?.readText(Charsets.UTF_8)
                ?: throw IllegalStateException("Ресурс не найден: $resourcePath")
        } catch (e: Exception) {
            logger.error("Не удалось прочитать файл прайс-листа: $resourcePath", e)
            throw e
        }

        val json = Json { ignoreUnknownKeys = true }

        return try {
            json.decodeFromString<T>(jsonContent)
        } catch (e: Exception) {
            logger.error("Не удалось распарсить JSON из файла: $resourcePath", e)
            throw e
        }
    }
}