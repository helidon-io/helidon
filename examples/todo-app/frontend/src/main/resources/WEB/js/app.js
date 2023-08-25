/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global jQuery, Router */

/**
 * @typedef {Object} Handlebars
 * @property {function(string, function(*, *, HandlebarsOpts))} registerHelper
 * @property {function(HTMLElement):function(object):string} compile
 */

/**
 * @typedef {Object} HandlebarsOpts
 * @property {function(object)} fn
 * @property {function(object)} inverse
 */

/**
 * @typedef {Object} Google
 * @property {GoogleAccount} accounts
 */

/**
 * @typedef {Object} GoogleAccount
 * @property {GoogleId} id
 */

/**
 * @typedef {Object} GoogleId
 * @property {function({})} initialize
 * @property {function()} prompt
 * @property {function(Element, Object)} renderButton
 * @property {function(string} revoke
 */

/**
 * @typedef {Object} Todo
 * @property {string} title
 * @property {string=} id
 * @property {boolean} completed
 */

class TodoClient {

  constructor(access_token) {
    this.access_token = access_token
  }

  /**
   * List all entries.
   * @return {Promise<Todo[]>}
   */
  list() {
    return this._ajax('GET', '/api/todo');
  }

  /**
   * Create a new entry.
   * @property {Todo} data
   * @return {Promise<Todo>}
   */
  create(item) {
    return this._ajax('POST', '/api/todo', item);
  }

  /**
   * Toggle an entry.
   * @param {Todo} item
   * @param {boolean} completed
   */
  toggle(item, completed) {
    const data = {...item, completed};
    return this._ajax('PUT', `/api/todo/${item.id}`, data);
  }

  /**
   * Update an entry.
   * @param {Todo} item
   * @return {Promise<Todo>}
   */
  update(item) {
    return this._ajax('PUT', `/api/todo/${item.id}`, item);
  }

  /**
   * Delete an entry.
   * @param {string} id
   * @return {Promise<Todo>}
   */
  delete(id) {
    return this._ajax('DELETE', `/api/todo/${id}`);
  }

  /**
   * Batch requests.
   * @param {Todo[]} items
   * @param {function(Todo):boolean} filter
   * @param {function(Todo, number): Promise<Todo>} fn
   * @return {Promise<Awaited<unknown>[]>}
   */
  batch(items, filter, fn) {
    const promises = [];
    items.forEach((e, i) => {
      if (filter(e)) {
        promises.push(fn(e, i));
      }
    })
    return Promise.all(promises);
  }

  /**
   * Toggle all items.
   * @property {Todo[]} items
   * @property {boolean} completed
   * @return {Promise<Todo[]>}
   */
  toggleAll(items, completed) {
    const result = [...items];
    return this.batch(items, e => e.completed !== completed, (e, i) => {
      return this.toggle(e, completed).then(data => {
        result[i] = data;
      })
    }).then(() => result);
  }

  /**
   * Delete all completed items.
   * @param {Todo[]} items
   * @return {Promise<Todo[]>}
   */
  deleteCompleted(items) {
    const indexes = [];
    const result = [...items];
    return this.batch(items, e => e.completed, (data, index) => {
      indexes.push(index);
      return this._ajax('DELETE', `/api/todo/${data.id}`);
    }).then(() => {
      indexes.sort();
      for (let i = indexes.length - 1; i >= 0; i--) {
        result.splice(indexes[i], 1);
      }
      return result;
    })
  }

  /**
   * @param {string} type
   * @param {string} path
   * @param {object=} data
   * @return {Promise<object>}
   */
  _ajax(type, path, data) {
    while (path.startsWith('/')) {
      path = path.substring(1);
    }
    return new Promise((resolve, reject) => {
      // noinspection JSUnusedGlobalSymbols
      $.ajax({
        type: type,
        beforeSend: (request) => {
          if (this.access_token) {
            request.setRequestHeader('Authorization', `Bearer ${this.access_token}`);
          }
        },
        url: window.location.pathname + path,
        dataType: 'json',
        contentType: 'application/json;charset=utf-8',
        data: data && JSON.stringify(data)
      }).done((resData) => {
        resolve(resData);
      }).fail((data, textStatus) => {
        reject(textStatus);
      });
    })
  }
}

class App {

  constructor() {
    this.client = null;
    this.token = null;
    this.todos = [];
    this.todoTemplate = Handlebars.compile($('#todo-template').html());
    this.footerTemplate = Handlebars.compile($('#footer-template').html());
    this.router = new Router({
      '/:filter': (filter) => {
        this.filter = filter;
        this.render();
      }
    });
    $('.sign-out').on('click', e => this.signOut());
    $('.new-todo').on('keyup', e => this.create(e));
    $('.toggle-all').on('change', e => this.toggleAll(e));
    $('.footer').on('click', '.clear-completed', () => this.deleteCompleted());
    $('.todo-list')
      .on('change', '.toggle', e => this.toggle(e))
      .on('click', '.update', e => this.editingMode(e))
      .on('focusout', '.edit', e => this.update(e))
      .on('click', '.delete', e => this.destroy(e));
  }

  signOut() {
    const google = /** @type {Google} */ (window['google']);
    google.accounts.id.revoke(this.token.sub);
    this.client = null;
    this.token = null;
    $('#user-info').fadeOut();
    $('.wrap').fadeOut();
    google.accounts.id.prompt();
  }

  /**
   * Init the application.
   */
  init(access_token) {
    this.client = new TodoClient(access_token);
    this.token = JSON.parse(atob(access_token.split(".")[1]));
    const signedIn = access_token && true || false;
    if (signedIn) {
      this.init0().then(() => {
        $('.wrap').fadeIn();
        $('#user-info').fadeIn();
      })
    }
  }

  init0() {
    return this.client.list().then(items => {
      this.todos = items;
      this.router.init('/all');
    }).catch(console.error);
  }

  /**
   * Render.
   */
  render() {
    const todos = this.getFilteredTodos();
    $('.todo-list').html(this.todoTemplate(todos.map((e, i) => {
      e.index = i;
      return e;
    })));
    $('.main').toggle(todos.length > 0);
    $('.toggle-all').prop('checked', this.getActiveTodos().length === 0);
    this.renderFooter();
    $('.new-todo').focus();
  }

  /**
   * Render the footer.
   */
  renderFooter() {
    const todoCount = this.todos.length;
    const activeTodoCount = this.getActiveTodos().length;
    const template = this.footerTemplate({
      activeTodoCount: activeTodoCount,
      activeTodoWord: this.pluralize(activeTodoCount, 'item'),
      completedTodos: todoCount - activeTodoCount,
      filter: this.filter
    });
    $('.footer').toggle(todoCount > 0).html(template);
  }

  /**
   * Pluralize the given word.
   * @param {number} count
   * @param {string} word
   * @return {string}
   */
  pluralize(count, word) {
    return count === 1 ? word : word + 's';
  }

  /**
   * Get the active entries.
   * @return {Todo[]}
   */
  getActiveTodos() {
    return this.todos.filter((todo) => {
      return !todo.completed;
    });
  }

  /**
   * Get the completed entries.
   * @return {Todo[]}
   */
  getCompletedTodos() {
    return this.todos.filter((todo) => todo.completed);
  }

  /**
   * Get the entries for the current filter.
   * @return {Todo[]}
   */
  getFilteredTodos() {
    if (this.filter === 'active') {
      return this.getActiveTodos();
    }
    if (this.filter === 'completed') {
      return this.getCompletedTodos();
    }
    return this.todos;
  }

  /**
   * Toggle all entries.
   * @param {Event} e
   */
  toggleAll(e) {
    const isChecked = $(e.target).prop('checked');
    this.client.toggleAll(this.todos, isChecked).then(items => {
      this.todos = items;
      this.render();
    }).catch(console.error);
  }

  /**
   * Delete all completed entries.
   */
  deleteCompleted() {
    this.client.deleteCompleted(this.todos).then(items => {
      this.todos = items;
      this.render();
    }).catch(console.error);
  }

  /**
   * Create a new entry.
   * @param {KeyboardEvent} e
   */
  create(e) {
    const input = $(e.target);
    const val = input.val().trim();
    if (e.key !== "Enter" || !val) {
      return;
    }
    input.val('');
    const item = {title: val, completed: false};
    this.client.create(item).then((data) => {
      this.todos.push(data);
      this.render();
    }).catch(console.error)
  }

  /**
   * Toggle an entry.
   * @param {Event} e
   * @return {Promise<Object | void>}
   */
  toggle(e) {
    const info = this.entryInfo(e.target);
    const entry = this.todos[info.index];
    return this.client.toggle(entry, !entry.completed).then((data) => {
      this.todos[info.index] = data;
      this.render();
    }).catch(console.error);
  }

  /**
   * Update an entry.
   * @param {{target: Element}} e
   * @return {Promise<void>}
   */
  update(e) {
    const input = $(e.target);
    const val = input.val().trim();
    if (!val) {
      this.destroy(e);
      return Promise.resolve();
    } else {
      const info = this.entryInfo(e.target);
      const newData = {...this.todos[info.index], title: val};
      return this.client.update(newData).then(data => {
        this.todos[info.index] = data;
        this.render();
      }).catch(console.error);
    }
  }

  /**
   * Destroy an entry.
   * @param {{target: Element}} e
   */
  destroy(e) {
    const info = this.entryInfo(e.target);
    this.client.delete(info.id).then(() => {
      this.todos.splice(info.index, 1);
      this.render();
    }).catch(console.error);
  }

  /**
   * Edit an entry.
   * @param {Event} e
   */
  editingMode(e) {
    const listElt = $(e.target).closest('li');
    const editing = listElt.hasClass('editing');
    if (editing) {
      const input = listElt.find('.edit');
      this.update({
        target: input[0]
      }).then(() => listElt.removeClass('editing'));
    } else {
      const input = listElt.addClass('editing').find('.edit');
      // puts caret at end of input
      const tmpStr = input.val();
      input.val('');
      input.val(tmpStr);
      input.focus();
    }
  }

  /**
   * Get the entry info for an element.
   * @param {Element} el
   * @return {{index: number, id: string}}
   */
  entryInfo(el) {
    const id = $(el).closest('li').data('id');
    const todos = this.todos;
    let i = todos.length;
    while (i--) {
      if (todos[i].id === id) {
        return {id, index: i};
      }
    }
  }
}

window.onload = () => {
  const google = /** @type {Google} */ (window['google']);
  const Handlebars = /** @type {Handlebars} */ (window['Handlebars']);

  Handlebars.registerHelper('eq', (a, b, options) => {
    return a === b ? options.fn(this) : options.inverse(this);
  });

  const app = new App();

  // noinspection JSUnusedGlobalSymbols,SpellCheckingInspection
  google.accounts.id.initialize({
    client_id: '1048216952820-6a6ke9vrbjlhngbc0al0dkj9qs9tqbk2.apps.googleusercontent.com',
    auto_select: true,
    callback: response => {
      app.init(response.credential)
    }
  });
  google.accounts.id.prompt();
}
