package com.mlreef.rest.api

import com.mlreef.rest.Account
import com.mlreef.rest.DataProject
import com.mlreef.rest.DataProjectRepository
import com.mlreef.rest.api.v1.DataProjectCreateRequest
import com.mlreef.rest.api.v1.dto.DataProjectDto
import com.mlreef.rest.findById2
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.annotation.Rollback
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.HttpClientErrorException
import java.util.*
import javax.transaction.Transactional

class DataProjectsApiTest : RestApiTest() {
    @Autowired
    private lateinit var dataProjectRepository: DataProjectRepository

    private lateinit var account2: Account

    val rootUrl = "/api/v1/data-projects"

    @BeforeEach
    @AfterEach
    fun setUp() {
        dataProjectRepository.deleteAll()

        accountTokenRepository.deleteAll()
        accountRepository.deleteAll()
        personRepository.deleteAll()

        account = createMockUser()
        account2 = createMockUser(userOverrideSuffix = "0002")
    }

    @Transactional
    @Rollback
    @Test fun `Can create DataProject`() {
        val request = DataProjectCreateRequest("test-project", "Test project")
        val returnedResult = this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.post(rootUrl))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andDo(MockMvcRestDocumentation.document(
                "dataprojects-create",
                responseFields(dataProjectCreateRequestFields())))
            .andReturn().let {
                objectMapper.readValue(it.response.contentAsByteArray, DataProjectDto::class.java)
            }

        assertThat(returnedResult).isNotNull
    }

    @Transactional
    @Rollback
    @Test fun `Cannot create duplicate DataProject`() {
        Mockito.`when`(restClient.createProject(
            Mockito.anyString(), Mockito.anyString(), anyObject()
        )).then {
            throw HttpClientErrorException(HttpStatus.CONFLICT)
        }

        val request = DataProjectCreateRequest("test-project", "Test project")
        this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.post(rootUrl))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Transactional
    @Rollback
    @Test fun `Cannot create DataProject with invalid params`() {
        Mockito.`when`(restClient.createProject(
            Mockito.anyString(), Mockito.anyString(), anyObject()
        )).then {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST)
        }

        val request = DataProjectCreateRequest("", "")
        this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.post(rootUrl))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Transactional
    @Rollback
    @Test fun `Can retrieve all own DataProjects only`() {
        val project1 = DataProject(UUID.randomUUID(), "slug-1", "www.url.com", "Test Project 1", account.person.id, "group1", "project-1", 1, listOf())
        val project2 = DataProject(UUID.randomUUID(), "slug-2", "www.url.net", "Test Project 2", account.person.id, "group2", "project-2", 2, listOf())
        val project3 = DataProject(UUID.randomUUID(), "slug-3", "www.url.xyz", "Test Project 3", account2.person.id, "group3", "project-3", 3, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val returnedResult: List<DataProjectDto> = this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.get(rootUrl)))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andDo(MockMvcRestDocumentation.document(
                "dataprojects-retrieve-all",
                responseFields(dataProjectCreateRequestFields("[]."))))
            .andReturn().let {
                val constructCollectionType = objectMapper.typeFactory.constructCollectionType(List::class.java, DataProjectDto::class.java)
                objectMapper.readValue(it.response.contentAsByteArray, constructCollectionType)
            }

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test fun `Can retrieve specific own DataProject`() {
        val id1 = UUID.randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", account.person.id, "group1", "project-1", 1, listOf())
        val project2 = DataProject(UUID.randomUUID(), "slug-2", "www.url.net", "Test Project 2", account.person.id, "group2", "project-2", 2, listOf())
        val project3 = DataProject(UUID.randomUUID(), "slug-3", "www.url.xyz", "Test Project 3", account2.person.id, "group3", "project-3", 3, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val returnedResult: DataProjectDto = this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.get("$rootUrl/$id1")))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andDo(MockMvcRestDocumentation.document(
                "dataprojects-retrieve-one",
                responseFields(dataProjectCreateRequestFields())))
            .andReturn().let {
                objectMapper.readValue(it.response.contentAsByteArray, DataProjectDto::class.java)
            }

        assertThat(returnedResult.id).isEqualTo(id1)
        assertThat(returnedResult.gitlabProject).isEqualTo("project-1")
    }

    @Transactional
    @Rollback
    @Test fun `Cannot retrieve specific not own DataProject`() {
        val id1 = UUID.randomUUID()
        val project1 = DataProject(UUID.randomUUID(), "slug-1", "www.url.com", "Test Project 1", account.person.id, "group1", "project-1", 1, listOf())
        val project2 = DataProject(UUID.randomUUID(), "slug-2", "www.url.net", "Test Project 2", account.person.id, "group2", "project-2", 2, listOf())
        val project3 = DataProject(id1, "slug-3", "www.url.xyz", "Test Project 3", account2.person.id, "group3", "project-3", 3, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.get("$rootUrl/$id1")))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Transactional
    @Rollback
    @Test fun `Can update own DataProject`() {
        val id1 = UUID.randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", account.person.id, "group1", "project-1", 1, listOf())
        dataProjectRepository.save(project1)

        val request = DataProjectCreateRequest("test-project", "New Test project")

        val returnedResult = this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.put("$rootUrl/$id1"))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andDo(MockMvcRestDocumentation.document(
                "dataprojects-update",
                responseFields(dataProjectCreateRequestFields())))
            .andReturn().let {
                objectMapper.readValue(it.response.contentAsByteArray, DataProjectDto::class.java)
            }

        assertThat(returnedResult.gitlabProject).isEqualTo("New Test project")
    }

    @Transactional
    @Rollback
    @Test fun `Cannot update not-own DataProject`() {
        val id1 = UUID.randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", account2.person.id, "group1", "project-1", 1)
        dataProjectRepository.save(project1)

        val request = DataProjectCreateRequest("test-project", "New Test project")

        this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.put("$rootUrl/$id1"))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Transactional
    @Rollback
    @Test fun `Can delete own DataProject`() {
        val id1 = UUID.randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", account.person.id, "group1", "project-1", 1, listOf())
        dataProjectRepository.save(project1)

        assertThat(dataProjectRepository.findById2(id1)).isNotNull

        // TODO document delete "dataprojects-delete"
        this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.delete("$rootUrl/$id1")))
            .andExpect(MockMvcResultMatchers.status().isNoContent)
            .andDo(MockMvcRestDocumentation.document(
                "dataprojects-delete"))

        assertThat(dataProjectRepository.findById2(id1)).isNull()
    }

    @Transactional
    @Rollback
    @Test fun `Cannot delete not-own DataProject`() {
        val id1 = UUID.randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", account2.person.id, "group1", "project-1", 1)
        dataProjectRepository.save(project1)

        assertThat(dataProjectRepository.findById2(id1)).isNotNull

        this.mockMvc.perform(
            this.defaultAcceptContentAuth(RestDocumentationRequestBuilders.delete("$rootUrl/$id1")))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }


    private fun dataProjectCreateRequestFields(prefix: String = ""): List<FieldDescriptor> {
        return listOf(
            fieldWithPath(prefix + "id").type(JsonFieldType.STRING).description("Data project id"),
            fieldWithPath(prefix + "slug").type(JsonFieldType.STRING).description("Data project slug"),
            fieldWithPath(prefix + "url").type(JsonFieldType.STRING).description("URL in Gitlab domain"),
            fieldWithPath(prefix + "owner_id").type(JsonFieldType.STRING).description("Owner id of the data project"),
            fieldWithPath(prefix + "gitlab_group").type(JsonFieldType.STRING).description("The group where the project is in"),
            fieldWithPath(prefix + "gitlab_project").type(JsonFieldType.STRING).description("Project name"),
            fieldWithPath(prefix + "gitlab_id").type(JsonFieldType.NUMBER).description("Id in gitlab"),
            fieldWithPath(prefix + "experiments").type(JsonFieldType.ARRAY).optional().description("List of experiments inside the project (empty on creation)")
        )
    }
}
