package com.example.hellofigma.message

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hellofigma.message.model.Chat
import com.example.hellofigma.message.model.Message
import com.example.hellofigma.message.repository.ChatRepository
import com.example.hellofigma.message.repository.NetworkResult
import com.example.hellofigma.message.repository.SendMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import retrofit2.HttpException
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException


class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val _loadChatHistoryState = MutableStateFlow<NetworkResult<Unit>?>(null)
    val loadChatHistoryState: StateFlow<NetworkResult<Unit>?> = _loadChatHistoryState.asStateFlow()
    fun resetLoadChatHistoryState() {
        _loadChatHistoryState.value = null
    }

    private val repository = ChatRepository.getInstance(application)

    suspend fun getChatList(userId: String) {
        repository.getChatList(userId);
    }

    private val _chats = MutableStateFlow<List<Chat>?>(null)
    val chats: StateFlow<List<Chat>?> = _chats.asStateFlow()
    fun loadChats(userId: String) {
        viewModelScope.launch {
            repository.getChats(userId).collect { chatList ->
                _chats.value = chatList
            }
        }
    }

    private val _addChatState = mutableStateOf<NetworkResult<Chat>>(NetworkResult.Loading)
    val addChatState: State<NetworkResult<Chat>> = _addChatState

    fun addChat(userId: String, otherUserId: String, otherUserName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            handleAddChat(userId, otherUserId, otherUserName)
        }
    }

    private suspend fun handleAddChat(userId: String, otherUserId: String, otherUserName: String) {
        repository.addChat(userId, otherUserId, otherUserName)
            .collect { result -> _addChatState.value = result }
    }


    fun loadChatHistory(chatId: String, userId: String, otherUserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.getChatHistory(chatId = chatId, otherUserId = otherUserId, currentUserId = userId)
                withContext(Dispatchers.Main) {
                    _loadChatHistoryState.value = when (result) {
                        is NetworkResult.Success -> NetworkResult.Success(Unit)
                        is NetworkResult.Error -> NetworkResult.Error(result.error ?: "Load chat history fail")
                        else -> NetworkResult.Error("Unknown error")
                    }
                }
            } catch (e: IOException) {
                _loadChatHistoryState.value = NetworkResult.Error("Network error: ${e.message}")
            } catch (e: HttpException) {
                _loadChatHistoryState.value = NetworkResult.Error("Server error: ${e.code()}")
            } catch (e: JsonParseException) {
                _loadChatHistoryState.value = NetworkResult.Error("Data parsing error")
            }

        }
    }

    fun getMessages(chatId: String): Flow<List<Message>> {
        return repository.getLocalMessages(chatId)
    }

    fun sendMessage(chatId: String, userId: String, otherUserId: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 调用API发送消息
                repository.apiService.sendMessage(
                    chatId = chatId,
                    request = SendMessageRequest(
                        senderId = userId,
                        message = message
                    )
                )

                repository.getChatHistory(chatId, otherUserId, userId)
            } catch (e: IOException) {
                Log.e("sendMessage", "Network error while sending message: ${e.message}", e)
            } catch (e: HttpException) {
                Log.e("sendMessage", "HTTP error while sending message: ${e.code()} ${e.message()}", e)
            } catch (e: JsonParseException) {
                Log.e("sendMessage", "JSON parsing error while sending message: ${e.message}", e)
            }
        }
    }
}
