import React, { useState, useEffect, useRef } from 'react';
import './App.css';

function App() {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [showDropdown, setShowDropdown] = useState(false);
  const [message, setMessage] = useState(null);
  
  const dropdownRef = useRef(null);
  const debounceTimer = useRef(null);

  useEffect(() => {
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }

    if (query.trim() === '') {
      setSuggestions([]);
      setShowDropdown(false);
      return;
    }

    debounceTimer.current = setTimeout(() => {
      fetchSuggestions(query);
    }, 300);

    return () => clearTimeout(debounceTimer.current);
  }, [query]);

  const fetchSuggestions = async (prefix) => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(`http://localhost:8080/suggest?q=${encodeURIComponent(prefix)}`);
      if (!response.ok) throw new Error('Failed to fetch suggestions');
      const data = await response.json();
      setSuggestions(data);
      setShowDropdown(true);
      setSelectedIndex(-1);
    } catch (err) {
      setError('Something went wrong');
      setSuggestions([]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSearch = async (searchTerm) => {
    const finalTerm = searchTerm || query;
    if (!finalTerm.trim()) return;

    try {
      const response = await fetch('http://localhost:8080/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: finalTerm.trim() }),
      });

      if (response.ok) {
        showToast('Searched successfully!');
        setQuery('');
        setShowDropdown(false);
      } else {
        showToast('Search failed', true);
      }
    } catch (err) {
      showToast('Connection error', true);
    }
  };

  const showToast = (msg, isError = false) => {
    setMessage({ text: msg, isError });
    setTimeout(() => setMessage(null), 3000);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => (prev < suggestions.length - 1 ? prev + 1 : prev));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => (prev > -1 ? prev - 1 : -1));
    } else if (e.key === 'Enter') {
      if (selectedIndex >= 0 && suggestions[selectedIndex]) {
        handleSearch(suggestions[selectedIndex].query);
      } else {
        handleSearch(query);
      }
    } else if (e.key === 'Escape') {
      setShowDropdown(false);
    }
  };

  return (
    <div className="google-app">
      <div className="main-content">
        <div className="logo">
          <span className="blue">G</span>
          <span className="red">o</span>
          <span className="yellow">o</span>
          <span className="blue">g</span>
          <span className="green">l</span>
          <span className="red">e</span>
        </div>

        <div className="search-box-wrapper">
          <div className={`search-box ${showDropdown && suggestions.length > 0 ? 'has-suggestions' : ''}`}>
            <div className="search-icon">
              <svg focusable="false" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"></path></svg>
            </div>
            
            <input
              type="text"
              className="search-input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              onFocus={() => query.trim() && setShowDropdown(true)}
              autoFocus
            />

            {isLoading && <div className="spinner"></div>}
            
            <div className="right-tools">
              {query && (
                <button className="clear-btn" onClick={() => setQuery('')}>
                  <svg focusable="false" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"></path></svg>
                </button>
              )}
            </div>

            {showDropdown && query.trim() !== '' && (
              <div className="dropdown" ref={dropdownRef}>
                {error ? (
                  <div className="dropdown-info error">{error}</div>
                ) : suggestions.length > 0 ? (
                  <>
                    <div className="dropdown-divider"></div>
                    <ul className="suggestion-list">
                      {suggestions.map((item, index) => (
                        <li
                          key={index}
                          className={`suggestion-item ${index === selectedIndex ? 'active' : ''}`}
                          onClick={() => handleSearch(item.query)}
                          onMouseEnter={() => setSelectedIndex(index)}
                        >
                          <div className="item-icon">
                            <svg focusable="false" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"></path></svg>
                          </div>
                          <div className="item-content">
                            <span className="suggestion-text">{item.query}</span>
                            <span className="suggestion-count">{item.count.toLocaleString()} searches</span>
                          </div>
                        </li>
                      ))}
                    </ul>
                    <div className="dropdown-footer">
                      <button className="footer-btn" onClick={() => handleSearch()}>Google Search</button>
                      <button className="footer-btn">I'm Feeling Lucky</button>
                    </div>
                  </>
                ) : !isLoading && (
                  <div className="dropdown-info">No results found for "{query}"</div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="button-row">
          <button className="google-btn" onClick={() => handleSearch()}>Google Search</button>
          <button className="google-btn">I'm Feeling Lucky</button>
        </div>
      </div>
      
      {message && (
        <div className={`toast ${message.isError ? 'error' : ''}`}>
          {message.text}
        </div>
      )}
    </div>
  );
}

export default App;
