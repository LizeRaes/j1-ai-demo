// Use proxy endpoint through our own API to avoid CORS issues
const DOCUMENTS_API_BASE = '/api/documents';

/**
 * Fetch document content by document name
 * @param {string} documentName - Name of the document (e.g., "Known_Bugs_Limitations.txt")
 * @returns {Promise<string>} Document content as plain text
 */
/**
 * Extract document name from a document link or name
 * Handles formats like:
 * - "Approved_Response_Templates.txt" (just filename)
 * - "/documents/Approved_Response_Templates.txt" (path with /documents/)
 * - "http://example.com/documents/Approved_Response_Templates.txt" (full URL)
 */
function extractDocumentName(documentLinkOrName) {
    if (!documentLinkOrName) {
        return null;
    }

    // If it's already just a filename, return it
    if (!documentLinkOrName.includes('/')) {
        return documentLinkOrName;
    }

    // Extract filename from path
    // Handle both "/documents/name.txt" and "http://.../documents/name.txt"
    const parts = documentLinkOrName.split('/');
    return parts[parts.length - 1]; // Get the last part (filename)
}

export async function fetchDocumentContent(documentLinkOrName) {
    if (!documentLinkOrName) {
        throw new Error('Document name or link is required');
    }

    // Extract just the filename from the link
    const documentName = extractDocumentName(documentLinkOrName);
    if (!documentName) {
        throw new Error('Could not extract document name from: ' + documentLinkOrName);
    }

    const url = `${DOCUMENTS_API_BASE}/content/${encodeURIComponent(documentName)}`;
    console.log('Fetching document from:', url, '(extracted from:', documentLinkOrName + ')');

    try {
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'text/plain',
            },
            // Note: CORS is handled by the server, but if there are issues, 
            // the error will be caught below
        });

        console.log('Response status:', response.status, response.statusText);

        if (!response.ok) {
            const errorText = await response.text().catch(() => response.statusText);
            throw new Error(`Failed to fetch document (${response.status}): ${errorText || response.statusText}`);
        }

        let content = await response.text();
        console.log('Document fetched successfully, length:', content.length);

        // Check if response is JSON (documents API returns JSON with "content" field)
        if (content.trim().startsWith('{') && content.includes('"content"')) {
            try {
                const json = JSON.parse(content);
                if (json.content && typeof json.content === 'string') {
                    content = json.content;
                    console.log('Extracted content from JSON, length:', content.length);
                }
            } catch (e) {
                console.warn('Failed to parse JSON response, using raw content:', e);
            }
        }

        return content;
    } catch (error) {
        console.error('Error fetching document:', error);
        // Provide more helpful error messages
        if (error instanceof TypeError && error.message.includes('fetch')) {
            throw new Error(`Network error: Could not connect to documents API at ${DOCUMENTS_API_BASE}. Is the service running?`);
        }
        throw error;
    }
}
